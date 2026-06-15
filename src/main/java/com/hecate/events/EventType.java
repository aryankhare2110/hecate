package com.hecate.events;

public enum EventType {
    THREAD_START,
    THREAD_END,
    LOCK_WAIT,
    LOCK_RELEASE,
    LOCK_ACQUIRE
}

/*
 * Notes
 * - The kinds of event Hecate records. The lock events (WAIT, ACQUIRE, RELEASE) drive every
 *   analyzer; THREAD_START / THREAD_END are modelled but not currently emitted by the agent.
 */
