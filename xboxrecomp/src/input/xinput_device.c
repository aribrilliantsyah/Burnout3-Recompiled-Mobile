/**
 * Xbox Input Compatibility Layer
 *
 * Translates the Xbox controller API to a host gamepad backend.
 * Handles the structural differences between the Xbox gamepad
 * (analog buttons as bytes, separate trigger channels) and the host
 * (digital face buttons, trigger axes).
 *
 *   _WIN32 -> Windows XInput
 *   POSIX  -> SDL2 GameController
 */

#include "xinput_xbox.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* ======================================================================== */
#if defined(_WIN32)
/* ====================  XInput backend  ================================== */
/* ======================================================================== */

#include <xinput.h>
#pragma comment(lib, "xinput.lib")

static BOOL  g_controller_connected[XBOX_MAX_CONTROLLERS] = { FALSE };
static DWORD g_last_packet[XBOX_MAX_CONTROLLERS] = { 0 };

/* ---- keyboard, when there is no pad --------------------------------------
 *
 * A title that waits on PRESS START is unreachable on a machine with no
 * controller plugged in, which is most machines someone brings this up on.
 * The whole point of a recompilation is to be able to look at the thing
 * running, and a build nobody can press a button in cannot be looked at.
 *
 * Off by default, because a keyboard silently acting as player 1 is
 * surprising when a real pad is what you meant to use. RECOMP_KEYBOARD=1
 * turns it on. It only ever answers for port 0, and is merged on top of a
 * pad connected there, so a real controller keeps working.
 *
 * The keys are the ones a Dreamcast or Saturn emulator would pick, which is
 * the closest thing to a convention here:
 *
 *   arrows        d-pad             Enter      START
 *   Z X A S       A B X Y           Backspace  BACK
 *   Q E           white black       1 3        triggers
 *   numpad 8/2/4/6 left thumb       I/K/J/L    right thumb
 *
 * Keys come from the framebuffer window, which only receives them while it
 * has the focus, so typing in another window does not drive the game.
 */
static BOOL keyboard_enabled(void)
{
    static int on = -1;
    if (on < 0) {
        const char *v = getenv("RECOMP_KEYBOARD");
        on = (v && *v && *v != '0') ? 1 : 0;
    }
    return on ? TRUE : FALSE;
}

/* The framebuffer window records what is held; see src/video/fb_present.c.
 *
 * GetAsyncKeyState was the first attempt and reads nothing here -- under
 * Wine it answers about a state a GDI-drawing guest never touches. Going
 * through the window is also the better gate: a window only receives keys
 * while it has the focus, so there is no separate focus check to get wrong
 * and no way for typing in another application to drive the game.
 *
 * Declared rather than included so src/input does not depend on the video
 * module's include path. */
extern int xbox_FramebufferKeyDown(int vk);

static BOOL key_down(int vk)
{
    return xbox_FramebufferKeyDown(vk) ? TRUE : FALSE;
}

/* A thumb axis from two keys, at the full deflection a digital key implies. */
static SHORT axis_from_keys(int negative, int positive)
{
    int v = 0;
    if (key_down(negative)) v -= 32767;
    if (key_down(positive)) v += 32767;
    return (SHORT)v;
}

static void keyboard_state(XBOX_INPUT_STATE *pState)
{
    static DWORD packet;
    WORD b = 0;

    memset(pState, 0, sizeof(*pState));

    if (key_down(VK_UP))     b |= XBOX_GAMEPAD_DPAD_UP;
    if (key_down(VK_DOWN))   b |= XBOX_GAMEPAD_DPAD_DOWN;
    if (key_down(VK_LEFT))   b |= XBOX_GAMEPAD_DPAD_LEFT;
    if (key_down(VK_RIGHT))  b |= XBOX_GAMEPAD_DPAD_RIGHT;
    if (key_down(VK_RETURN)) b |= XBOX_GAMEPAD_START;
    if (key_down(VK_BACK))   b |= XBOX_GAMEPAD_BACK;
    if (key_down(VK_SHIFT))  b |= XBOX_GAMEPAD_LEFT_THUMB;
    if (key_down(VK_CONTROL))b |= XBOX_GAMEPAD_RIGHT_THUMB;
    pState->Gamepad.wButtons = b;

    /* Analog on the console, so a key is 255 rather than a flag -- a title
     * that reads these as a pressure never sees a press if they are 1. */
    pState->Gamepad.bAnalogButtons[XBOX_BUTTON_A]        = key_down('Z') ? 255 : 0;
    pState->Gamepad.bAnalogButtons[XBOX_BUTTON_B]        = key_down('X') ? 255 : 0;
    pState->Gamepad.bAnalogButtons[XBOX_BUTTON_X]        = key_down('A') ? 255 : 0;
    pState->Gamepad.bAnalogButtons[XBOX_BUTTON_Y]        = key_down('S') ? 255 : 0;
    pState->Gamepad.bAnalogButtons[XBOX_BUTTON_WHITE]    = key_down('Q') ? 255 : 0;
    pState->Gamepad.bAnalogButtons[XBOX_BUTTON_BLACK]    = key_down('E') ? 255 : 0;
    pState->Gamepad.bAnalogButtons[XBOX_BUTTON_LTRIGGER] = key_down('1') ? 255 : 0;
    pState->Gamepad.bAnalogButtons[XBOX_BUTTON_RTRIGGER] = key_down('3') ? 255 : 0;

    /* W/A/S/D would collide with the face buttons above, so the left thumb
     * shares the arrow keys' row on the numeric pad instead. */
    pState->Gamepad.sThumbLX = axis_from_keys(VK_NUMPAD4, VK_NUMPAD6);
    pState->Gamepad.sThumbLY = axis_from_keys(VK_NUMPAD2, VK_NUMPAD8);
    pState->Gamepad.sThumbRX = axis_from_keys('J', 'L');
    pState->Gamepad.sThumbRY = axis_from_keys('K', 'I');

    /* The title's input layer looks for button edges, so the packet number
     * has to move whenever the state does or a press is never noticed. */
    pState->dwPacketNumber = ++packet;
}

void xbox_InputInit(void)
{
    for (DWORD i = 0; i < XBOX_MAX_CONTROLLERS; i++) {
        XINPUT_STATE state;
        DWORD result = XInputGetState(i, &state);
        g_controller_connected[i] = (result == ERROR_SUCCESS);
    }
}

DWORD xbox_InputGetState(DWORD dwPort, XBOX_INPUT_STATE *pState)
{
    XINPUT_STATE xi_state;
    DWORD result;

    if (dwPort >= XBOX_MAX_CONTROLLERS || !pState)
        return ERROR_DEVICE_NOT_CONNECTED;

    result = XInputGetState(dwPort, &xi_state);
    if (result != ERROR_SUCCESS) {
        g_controller_connected[dwPort] = FALSE;
        if (dwPort == 0 && keyboard_enabled()) {
            keyboard_state(pState);
            return ERROR_SUCCESS;
        }
        return result;
    }

    g_controller_connected[dwPort] = TRUE;
    g_last_packet[dwPort] = xi_state.dwPacketNumber;

    memset(pState, 0, sizeof(XBOX_INPUT_STATE));
    pState->dwPacketNumber = xi_state.dwPacketNumber;
    pState->Gamepad.wButtons = xi_state.Gamepad.wButtons & 0x00FF;

    pState->Gamepad.bAnalogButtons[XBOX_BUTTON_A] =
        (xi_state.Gamepad.wButtons & XINPUT_GAMEPAD_A) ? 255 : 0;
    pState->Gamepad.bAnalogButtons[XBOX_BUTTON_B] =
        (xi_state.Gamepad.wButtons & XINPUT_GAMEPAD_B) ? 255 : 0;
    pState->Gamepad.bAnalogButtons[XBOX_BUTTON_X] =
        (xi_state.Gamepad.wButtons & XINPUT_GAMEPAD_X) ? 255 : 0;
    pState->Gamepad.bAnalogButtons[XBOX_BUTTON_Y] =
        (xi_state.Gamepad.wButtons & XINPUT_GAMEPAD_Y) ? 255 : 0;
    pState->Gamepad.bAnalogButtons[XBOX_BUTTON_BLACK] =
        (xi_state.Gamepad.wButtons & XINPUT_GAMEPAD_LEFT_SHOULDER) ? 255 : 0;
    pState->Gamepad.bAnalogButtons[XBOX_BUTTON_WHITE] =
        (xi_state.Gamepad.wButtons & XINPUT_GAMEPAD_RIGHT_SHOULDER) ? 255 : 0;
    pState->Gamepad.bAnalogButtons[XBOX_BUTTON_LTRIGGER] = xi_state.Gamepad.bLeftTrigger;
    pState->Gamepad.bAnalogButtons[XBOX_BUTTON_RTRIGGER] = xi_state.Gamepad.bRightTrigger;

    pState->Gamepad.sThumbLX = xi_state.Gamepad.sThumbLX;
    pState->Gamepad.sThumbLY = xi_state.Gamepad.sThumbLY;
    pState->Gamepad.sThumbRX = xi_state.Gamepad.sThumbRX;
    pState->Gamepad.sThumbRY = xi_state.Gamepad.sThumbRY;

    /* Merge the keyboard on top rather than only standing in for a missing
     * pad.
     *
     * The first version put the keyboard behind XInput's failure, on the
     * assumption that with nothing plugged in the call would fail. It does
     * not: under Wine XInputGetState returns ERROR_SUCCESS and a gamepad
     * with every button at rest, so the fallback was unreachable and
     * pressing a key did nothing at all.
     *
     * Merging is also the better rule. A real pad keeps working -- its
     * buttons are already in pState and the keyboard only adds to them --
     * and there is no special case left to get wrong. */
    if (dwPort == 0 && keyboard_enabled()) {
        XBOX_INPUT_STATE kb;
        int i;
        keyboard_state(&kb);
        pState->Gamepad.wButtons |= kb.Gamepad.wButtons;
        for (i = 0; i < 8; i++)
            if (kb.Gamepad.bAnalogButtons[i] > pState->Gamepad.bAnalogButtons[i])
                pState->Gamepad.bAnalogButtons[i] = kb.Gamepad.bAnalogButtons[i];
        if (kb.Gamepad.sThumbLX) pState->Gamepad.sThumbLX = kb.Gamepad.sThumbLX;
        if (kb.Gamepad.sThumbLY) pState->Gamepad.sThumbLY = kb.Gamepad.sThumbLY;
        if (kb.Gamepad.sThumbRX) pState->Gamepad.sThumbRX = kb.Gamepad.sThumbRX;
        if (kb.Gamepad.sThumbRY) pState->Gamepad.sThumbRY = kb.Gamepad.sThumbRY;
        /* The input layer records edges, so an unchanged packet number is
         * read as the same state and the press never happens. */
        pState->dwPacketNumber = kb.dwPacketNumber;
    }

    return ERROR_SUCCESS;
}

DWORD xbox_InputSetState(DWORD dwPort, const XBOX_VIBRATION *pVibration)
{
    XINPUT_VIBRATION xi_vib;

    if (dwPort >= XBOX_MAX_CONTROLLERS || !pVibration)
        return ERROR_DEVICE_NOT_CONNECTED;

    xi_vib.wLeftMotorSpeed = pVibration->wLeftMotorSpeed;
    xi_vib.wRightMotorSpeed = pVibration->wRightMotorSpeed;
    return XInputSetState(dwPort, &xi_vib);
}

BOOL xbox_InputIsConnected(DWORD dwPort)
{
    if (dwPort >= XBOX_MAX_CONTROLLERS) return FALSE;
    return g_controller_connected[dwPort];
}

DWORD xbox_InputGetCapabilities(DWORD dwPort, DWORD dwFlags, XBOX_INPUT_CAPABILITIES *pCaps)
{
    XINPUT_CAPABILITIES xi_caps;
    DWORD result;

    if (dwPort >= XBOX_MAX_CONTROLLERS || !pCaps)
        return ERROR_DEVICE_NOT_CONNECTED;

    result = XInputGetCapabilities(dwPort, dwFlags, &xi_caps);
    if (result != ERROR_SUCCESS) return result;

    memset(pCaps, 0, sizeof(XBOX_INPUT_CAPABILITIES));
    pCaps->Type = xi_caps.Type;
    pCaps->SubType = xi_caps.SubType;
    pCaps->Flags = xi_caps.Flags;
    return ERROR_SUCCESS;
}

/* ======================================================================== */
#else /* !_WIN32 */
/* ====================  SDL2 GameController backend  ===================== */
/* ======================================================================== */

#include <SDL.h>

static SDL_GameController *g_pads[XBOX_MAX_CONTROLLERS];
static BOOL  g_controller_connected[XBOX_MAX_CONTROLLERS];
static DWORD g_packet[XBOX_MAX_CONTROLLERS];

/* ---- virtual pad (Android touch overlay), always port 0 --------------
 * Written by the UI thread through xbox_InputSetVirtual, read by the game
 * thread; each field is a plain int, a torn read only mixes two frames. */
static volatile int  g_virt_on;
static volatile int  g_virt_buttons;        /* XBOX_GAMEPAD_* digital bits */
static volatile int  g_virt_face;           /* bit n = analog button n (A,B,X,Y,BLACK,WHITE) */
static volatile int  g_virt_lt, g_virt_rt;  /* 0..255 */
static volatile int  g_virt_lx, g_virt_ly;  /* -32768..32767, Y up */
static volatile int  g_virt_rx, g_virt_ry;

void xbox_InputSetVirtual(int buttons, int face, int lt, int rt, int lx, int ly)
{
    g_virt_buttons = buttons;
    g_virt_face = face;
    g_virt_lt = lt;
    g_virt_rt = rt;
    g_virt_lx = lx;
    g_virt_ly = ly;
    g_virt_on = 1;
}

void xbox_InputSetVirtualRight(int rx, int ry)
{
    g_virt_rx = rx;
    g_virt_ry = ry;
}

/* ---- rumble to the host (Android: the phone's vibrator when no pad) ---- */
static void (*g_rumble_sink)(int left, int right);

void xbox_InputSetRumbleSink(void (*sink)(int left, int right))
{
    g_rumble_sink = sink;
}

/* ---- physical pad remap ------------------------------------------------
 * RECOMP_PAD_MAP=<file>: lines "xbox=sdl", e.g. "a=b", "black=leftshoulder",
 * "lt=lefttrigger", "rt=a". xbox: a b x y black white lt rt up down left right
 * start back lthumb rthumb. sdl: SDL_GameControllerGetStringForButton /
 * ForAxis names (a b x y back guide start leftstick rightstick leftshoulder
 * rightshoulder dpup dpdown dpleft dpright misc1 paddle1-4 touchpad,
 * lefttrigger righttrigger), or "none". Also "deadzone=<0..50>" (percent,
 * sticks) and "invert_ly=1" / "invert_ry=1". */
enum { PM_A, PM_B, PM_X, PM_Y, PM_BLACK, PM_WHITE, PM_LT, PM_RT, PM_UP, PM_DOWN,
       PM_LEFT, PM_RIGHT, PM_START, PM_BACK, PM_LTHUMB, PM_RTHUMB, PM_N };
static const char *const pm_names[PM_N] = {
    "a", "b", "x", "y", "black", "white", "lt", "rt", "up", "down",
    "left", "right", "start", "back", "lthumb", "rthumb" };
/* source: >= 0 an SDL button, <= -2 an SDL axis (-2 - axis), -1 none */
static int pm_src[PM_N];
static int pm_loaded, pm_deadzone, pm_inv_ly, pm_inv_ry;

static void pm_defaults(void)
{
    pm_src[PM_A] = SDL_CONTROLLER_BUTTON_A;
    pm_src[PM_B] = SDL_CONTROLLER_BUTTON_B;
    pm_src[PM_X] = SDL_CONTROLLER_BUTTON_X;
    pm_src[PM_Y] = SDL_CONTROLLER_BUTTON_Y;
    pm_src[PM_BLACK] = SDL_CONTROLLER_BUTTON_LEFTSHOULDER;
    pm_src[PM_WHITE] = SDL_CONTROLLER_BUTTON_RIGHTSHOULDER;
    pm_src[PM_LT] = -2 - SDL_CONTROLLER_AXIS_TRIGGERLEFT;
    pm_src[PM_RT] = -2 - SDL_CONTROLLER_AXIS_TRIGGERRIGHT;
    pm_src[PM_UP] = SDL_CONTROLLER_BUTTON_DPAD_UP;
    pm_src[PM_DOWN] = SDL_CONTROLLER_BUTTON_DPAD_DOWN;
    pm_src[PM_LEFT] = SDL_CONTROLLER_BUTTON_DPAD_LEFT;
    pm_src[PM_RIGHT] = SDL_CONTROLLER_BUTTON_DPAD_RIGHT;
    pm_src[PM_START] = SDL_CONTROLLER_BUTTON_START;
    pm_src[PM_BACK] = SDL_CONTROLLER_BUTTON_BACK;
    pm_src[PM_LTHUMB] = SDL_CONTROLLER_BUTTON_LEFTSTICK;
    pm_src[PM_RTHUMB] = SDL_CONTROLLER_BUTTON_RIGHTSTICK;
}

static void pm_load(void)
{
    const char *path = getenv("RECOMP_PAD_MAP");
    FILE *f;
    char line[128];

    pm_loaded = 1;
    pm_defaults();
    if (!path || !*path || !(f = fopen(path, "r")))
        return;
    while (fgets(line, sizeof line, f)) {
        char *eq = strchr(line, '='), *v;
        int i;
        if (!eq || line[0] == '#')
            continue;
        *eq = 0;
        v = eq + 1;
        v[strcspn(v, "\r\n ")] = 0;
        if (!strcmp(line, "deadzone")) { pm_deadzone = atoi(v); continue; }
        if (!strcmp(line, "invert_ly")) { pm_inv_ly = atoi(v); continue; }
        if (!strcmp(line, "invert_ry")) { pm_inv_ry = atoi(v); continue; }
        for (i = 0; i < PM_N; i++) {
            SDL_GameControllerButton b;
            SDL_GameControllerAxis a;
            if (strcmp(line, pm_names[i]))
                continue;
            if (!strcmp(v, "none")) { pm_src[i] = -1; break; }
            b = SDL_GameControllerGetButtonFromString(v);
            if (b != SDL_CONTROLLER_BUTTON_INVALID) { pm_src[i] = b; break; }
            a = SDL_GameControllerGetAxisFromString(v);
            if (a == SDL_CONTROLLER_AXIS_TRIGGERLEFT || a == SDL_CONTROLLER_AXIS_TRIGGERRIGHT)
                pm_src[i] = -2 - (int)a;
            break;
        }
    }
    fclose(f);
    fprintf(stderr, "[input] pad map from %s (deadzone %d%%)\n", path, pm_deadzone);
}

/* 0..255 */
static int pm_read(SDL_GameController *c, int i)
{
    int s = pm_src[i];
    if (s == -1)
        return 0;
    if (s >= 0)
        return SDL_GameControllerGetButton(c, (SDL_GameControllerButton)s) ? 255 : 0;
    return SDL_GameControllerGetAxis(c, (SDL_GameControllerAxis)(-2 - s)) >> 7;
}

static SHORT pm_stick(SHORT v)
{
    int dz = pm_deadzone * 32767 / 100;
    int a = v < 0 ? -v : v;
    if (!dz)
        return v;
    if (a <= dz)
        return 0;
    a = (int)((long)(a - dz) * 32767 / (32767 - dz));
    if (a > 32767) a = 32767;
    return (SHORT)(v < 0 ? -a : a);
}

static void virt_merge(XBOX_INPUT_STATE *s)
{
    int i, f = g_virt_face;
    s->Gamepad.wButtons |= (WORD)g_virt_buttons;
    for (i = 0; i < 6; i++)
        if (f & (1 << i))
            s->Gamepad.bAnalogButtons[i] = 255;
    if (g_virt_lt > s->Gamepad.bAnalogButtons[XBOX_BUTTON_LTRIGGER])
        s->Gamepad.bAnalogButtons[XBOX_BUTTON_LTRIGGER] = (BYTE)g_virt_lt;
    if (g_virt_rt > s->Gamepad.bAnalogButtons[XBOX_BUTTON_RTRIGGER])
        s->Gamepad.bAnalogButtons[XBOX_BUTTON_RTRIGGER] = (BYTE)g_virt_rt;
    if (g_virt_lx || g_virt_ly) {
        s->Gamepad.sThumbLX = (SHORT)g_virt_lx;
        s->Gamepad.sThumbLY = (SHORT)g_virt_ly;
    }
    if (g_virt_rx || g_virt_ry) {
        s->Gamepad.sThumbRX = (SHORT)g_virt_rx;
        s->Gamepad.sThumbRY = (SHORT)g_virt_ry;
    }
}

/* Open up to XBOX_MAX_CONTROLLERS attached game controllers. */
static void open_controllers(void)
{
    int slot = 0;
    for (int i = 0; i < SDL_NumJoysticks() && slot < XBOX_MAX_CONTROLLERS; i++) {
        if (!SDL_IsGameController(i))
            continue;
        if (!g_pads[slot]) {
            g_pads[slot] = SDL_GameControllerOpen(i);
            g_controller_connected[slot] = (g_pads[slot] != NULL);
        }
        slot++;
    }
}

void xbox_InputInit(void)
{
    if (!SDL_WasInit(SDL_INIT_GAMECONTROLLER))
        SDL_InitSubSystem(SDL_INIT_GAMECONTROLLER);
    open_controllers();
}

DWORD xbox_InputGetState(DWORD dwPort, XBOX_INPUT_STATE *pState)
{
    if (dwPort >= XBOX_MAX_CONTROLLERS || !pState)
        return ERROR_DEVICE_NOT_CONNECTED;

    SDL_GameController *c = g_pads[dwPort];
    if (!c) {
        /* A pad paired after boot (Bluetooth): look again once a second. */
        static Uint32 last_scan;
        Uint32 now = SDL_GetTicks();
        if (now - last_scan > 1000) {
            last_scan = now;
            open_controllers();
            c = g_pads[dwPort];
        }
    }
    if (!c || !SDL_GameControllerGetAttached(c)) {
        if (c) {
            SDL_GameControllerClose(c);
            g_pads[dwPort] = NULL;
        }
        if (dwPort == 0 && g_virt_on) {
            g_controller_connected[0] = TRUE;
            memset(pState, 0, sizeof(XBOX_INPUT_STATE));
            pState->dwPacketNumber = ++g_packet[0];
            virt_merge(pState);
            return ERROR_SUCCESS;
        }
        g_controller_connected[dwPort] = FALSE;
        return ERROR_DEVICE_NOT_CONNECTED;
    }

    SDL_GameControllerUpdate();
    g_controller_connected[dwPort] = TRUE;

    memset(pState, 0, sizeof(XBOX_INPUT_STATE));
    pState->dwPacketNumber = ++g_packet[dwPort];

    if (!pm_loaded)
        pm_load();
    {
        static const WORD dbits[] = {
            XBOX_GAMEPAD_DPAD_UP, XBOX_GAMEPAD_DPAD_DOWN, XBOX_GAMEPAD_DPAD_LEFT,
            XBOX_GAMEPAD_DPAD_RIGHT, XBOX_GAMEPAD_START, XBOX_GAMEPAD_BACK,
            XBOX_GAMEPAD_LEFT_THUMB, XBOX_GAMEPAD_RIGHT_THUMB };
        static const BYTE abtn[] = {
            XBOX_BUTTON_A, XBOX_BUTTON_B, XBOX_BUTTON_X, XBOX_BUTTON_Y,
            XBOX_BUTTON_BLACK, XBOX_BUTTON_WHITE, XBOX_BUTTON_LTRIGGER, XBOX_BUTTON_RTRIGGER };
        WORD btn = 0;
        int i;
        for (i = 0; i < 8; i++)
            if (pm_read(c, PM_UP + i) > 127)
                btn |= dbits[i];
        pState->Gamepad.wButtons = btn;
        /* Xbox face buttons and triggers are analog (0..255). */
        for (i = 0; i < 8; i++)
            pState->Gamepad.bAnalogButtons[abtn[i]] = (BYTE)pm_read(c, PM_A + i);
    }

    /* SDL Y axis points down; the Xbox Y axis points up -- invert.
     * Use (-1 - v) so v = -32768 does not overflow SHORT. */
    {
        SHORT ly = SDL_GameControllerGetAxis(c, SDL_CONTROLLER_AXIS_LEFTY);
        SHORT ry = SDL_GameControllerGetAxis(c, SDL_CONTROLLER_AXIS_RIGHTY);
        pState->Gamepad.sThumbLX = pm_stick(SDL_GameControllerGetAxis(c, SDL_CONTROLLER_AXIS_LEFTX));
        pState->Gamepad.sThumbLY = pm_stick((SHORT)(pm_inv_ly ? ly : -1 - ly));
        pState->Gamepad.sThumbRX = pm_stick(SDL_GameControllerGetAxis(c, SDL_CONTROLLER_AXIS_RIGHTX));
        pState->Gamepad.sThumbRY = pm_stick((SHORT)(pm_inv_ry ? ry : -1 - ry));
    }
    if (dwPort == 0 && g_virt_on)
        virt_merge(pState);

    return ERROR_SUCCESS;
}

DWORD xbox_InputSetState(DWORD dwPort, const XBOX_VIBRATION *pVibration)
{
    if (dwPort >= XBOX_MAX_CONTROLLERS || !pVibration)
        return ERROR_DEVICE_NOT_CONNECTED;

    SDL_GameController *c = g_pads[dwPort];
    if (!c || !SDL_GameControllerGetAttached(c)) {
        if (dwPort == 0 && g_rumble_sink)
            g_rumble_sink(pVibration->wLeftMotorSpeed, pVibration->wRightMotorSpeed);
        return c ? ERROR_SUCCESS : ERROR_DEVICE_NOT_CONNECTED;
    }

    /* SDL rumble needs a duration; refresh for ~1s on each call (the game
     * polls vibration continuously). */
    SDL_GameControllerRumble(c, pVibration->wLeftMotorSpeed,
                             pVibration->wRightMotorSpeed, 1000);
    return ERROR_SUCCESS;
}

BOOL xbox_InputIsConnected(DWORD dwPort)
{
    if (dwPort >= XBOX_MAX_CONTROLLERS) return FALSE;
    return g_controller_connected[dwPort] || (dwPort == 0 && g_virt_on);
}

DWORD xbox_InputGetCapabilities(DWORD dwPort, DWORD dwFlags, XBOX_INPUT_CAPABILITIES *pCaps)
{
    (void)dwFlags;
    if (dwPort >= XBOX_MAX_CONTROLLERS || !pCaps)
        return ERROR_DEVICE_NOT_CONNECTED;
    if (!g_pads[dwPort] && !(dwPort == 0 && g_virt_on))
        return ERROR_DEVICE_NOT_CONNECTED;

    memset(pCaps, 0, sizeof(XBOX_INPUT_CAPABILITIES));
    pCaps->Type    = 1;   /* XINPUT_DEVTYPE_GAMEPAD */
    pCaps->SubType = 1;   /* XINPUT_DEVSUBTYPE_GAMEPAD */
    pCaps->Flags   = 0;
    return ERROR_SUCCESS;
}

#endif /* _WIN32 */
