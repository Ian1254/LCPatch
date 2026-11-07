#pragma once

#include <cstddef>
#include <cstdint>
#include <cstring>

namespace lcpatch {

// Read-only, bounded discovery of a *known* verifier implementation after its
// ELF Build ID changes. Never infer a verifier from a generic function prologue.
// A candidate must match the full prior 32-byte entry AND the known BL site.
struct SignatureScanRange {
    uintptr_t begin = 0;
    uintptr_t end = 0;
};

struct VerificationSignature {
    const uint8_t *entryBytes = nullptr;
    size_t entrySize = 0;
    size_t methodSize = 0;
    size_t fileOpenCallOffset = 0;
};

struct VerificationSignatureMatch {
    uintptr_t address = 0;
    size_t signatureIndex = 0;
    unsigned candidateCount = 0;
    bool scanComplete = false;
};

// Reader(address, destination, length) must return false instead of accessing
// inaccessible pages. If any executable range cannot be read completely,
// reject discovery rather than assuming a fingerprint was globally unique.
template <typename Reader>
VerificationSignatureMatch findUniqueVerificationSignature(
    const SignatureScanRange *ranges, size_t rangeCount,
    const VerificationSignature *signatures, size_t signatureCount,
    Reader reader) {
    VerificationSignatureMatch result{};
    if (!ranges || !signatures || !rangeCount || !signatureCount) return result;

    constexpr size_t kChunk = 64 * 1024;
    constexpr size_t kLookahead = 512;
    constexpr size_t kMaxScanBytes = 256U * 1024U * 1024U;
    uint8_t buffer[kChunk + kLookahead];
    size_t totalBytes = 0;

    for (size_t s = 0; s < signatureCount; ++s) {
        const auto &signature = signatures[s];
        if (!signature.entryBytes || signature.entrySize == 0 ||
            signature.entrySize > kLookahead ||
            signature.fileOpenCallOffset > kLookahead - sizeof(uint32_t) ||
            signature.methodSize < signature.entrySize ||
            signature.methodSize < signature.fileOpenCallOffset + sizeof(uint32_t))
            return result;
    }

    for (size_t r = 0; r < rangeCount; ++r) {
        const auto &range = ranges[r];
        if (!range.begin || range.end <= range.begin ||
            range.begin > UINTPTR_MAX - 3) return result;
        const size_t length = range.end - range.begin;
        if (length > kMaxScanBytes - totalBytes) return result;
        totalBytes += length;

        uintptr_t cursor = (range.begin + 3) & ~uintptr_t(3);
        while (cursor < range.end) {
            const size_t remaining = range.end - cursor;
            const size_t advance = remaining < kChunk ? remaining : kChunk;
            const size_t requested = remaining < kChunk + kLookahead ?
                remaining : kChunk + kLookahead;
            if (!reader(cursor, buffer, requested)) return result;
            for (size_t offset = 0; offset < advance; offset += 4) {
                const uintptr_t candidate = cursor + offset;
                for (size_t s = 0; s < signatureCount; ++s) {
                    const auto &signature = signatures[s];
                    const size_t bytesNeeded = signature.fileOpenCallOffset + sizeof(uint32_t) >
                        signature.entrySize ? signature.fileOpenCallOffset + sizeof(uint32_t) :
                                              signature.entrySize;
                    if (range.end - candidate < signature.methodSize ||
                        requested - offset < bytesNeeded ||
                        std::memcmp(buffer + offset, signature.entryBytes, signature.entrySize) != 0)
                        continue;

                    uint32_t call = 0;
                    std::memcpy(&call, buffer + offset + signature.fileOpenCallOffset, sizeof(call));
                    if ((call & 0xfc000000U) != 0x94000000U) continue; // AArch64 BL
                    if (++result.candidateCount != 1) {
                        result.address = 0; // Ambiguous: cannot safely redirect reads.
                        result.scanComplete = true;
                        return result;
                    }
                    result.address = candidate;
                    result.signatureIndex = s;
                }
            }
            cursor += advance;
        }
    }
    result.scanComplete = true;
    return result;
}

} // namespace lcpatch
