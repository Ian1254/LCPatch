#pragma once

#include <cstdint>
#include <unwind.h>

namespace lcpatch {
struct VerificationRange {
    uintptr_t begin = 0;
    uintptr_t end = 0;
};

// Observe the current native stack only. No game instructions, managed API,
// process-wide state or integrity result are modified.
inline bool isResourceVerificationRead(VerificationRange range, VerificationRange image = {}) {
    if (!range.begin || range.end <= range.begin) return false;
    struct Walk { VerificationRange range; VerificationRange image; unsigned frames; bool matched; bool enteredImage; } walk{range, image, 0, false, false};
    _Unwind_Backtrace([](_Unwind_Context *context, void *argument) {
        auto &state = *static_cast<Walk *>(argument);
        const uintptr_t returnAddress = static_cast<uintptr_t>(_Unwind_GetIP(context));
        // Unwind IP is the instruction after the call, possibly exactly at a
        // function boundary. Attribute it to the preceding call instruction.
        const uintptr_t pc = returnAddress ? returnAddress - 1 : 0;
        if (state.image.begin && state.image.end > state.image.begin) {
            const bool inImage = pc >= state.image.begin && pc < state.image.end;
            if (state.enteredImage && !inImage) return _URC_END_OF_STACK;
            state.enteredImage |= inImage;
        }
        if (pc >= state.range.begin && pc < state.range.end) {
            state.matched = true;
            return _URC_END_OF_STACK;
        }
        return ++state.frames >= 64 ? _URC_END_OF_STACK : _URC_NO_REASON;
    }, &walk);
    return walk.matched;
}
} // namespace lcpatch
