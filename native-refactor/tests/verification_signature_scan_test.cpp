#include "../verification_signature_scan.h"
#include <array>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>

#define CHECK(v) do { if (!(v)) { std::fprintf(stderr, "FAIL line %d: %s\n", __LINE__, #v); std::exit(1); } ++checks; } while (false)

int main() {
    unsigned checks = 0;
    constexpr std::array<uint8_t, 32> knownMethodA = {
        0xff, 0x43, 0x01, 0xd1, 0xfe, 0x5f, 0x02, 0xa9,
        0xf6, 0x57, 0x03, 0xa9, 0xf4, 0x4f, 0x04, 0xa9,
        0xb7, 0xcd, 0x02, 0x90, 0x16, 0x95, 0x02, 0xf0,
        0xf4, 0x03, 0x02, 0x2a, 0xe8, 0x4a, 0x71, 0x39
    };
    constexpr std::array<uint8_t, 32> knownMethodB = {
        0xff, 0x43, 0x01, 0xd1, 0xfe, 0x5f, 0x02, 0xa9,
        0xf6, 0x57, 0x03, 0xa9, 0xf4, 0x4f, 0x04, 0xa9,
        0x57, 0xcd, 0x02, 0xd0, 0xb6, 0x94, 0x02, 0xd0,
        0xf4, 0x03, 0x02, 0x2a, 0xe8, 0x7a, 0x71, 0x39
    };
    constexpr std::array<uint8_t, 32> mask = {
        0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
        0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
        0x1f, 0x00, 0x00, 0x9f, 0x1f, 0x00, 0x00, 0x9f,
        0xff, 0xff, 0xff, 0xff, 0xff, 0x03, 0xc0, 0xff,
    };
    constexpr size_t kMethodSize = 0x1ec;
    constexpr size_t kFileStreamCall = 0x88;
    const lcpatch::VerificationSignature exact{
        knownMethodA.data(), knownMethodA.size(), kMethodSize, kFileStreamCall
    };
    const lcpatch::VerificationSignature masked{
        knownMethodA.data(), knownMethodA.size(), kMethodSize, kFileStreamCall, mask.data()
    };
    const lcpatch::VerificationSignature alternate{
        knownMethodB.data(), knownMethodB.size(), kMethodSize, kFileStreamCall, mask.data()
    };

    // This is the regression: known 1.117.1 and 1.118.0 must map to the same
    // masked instructions, while their exact fingerprints are different.
    CHECK(lcpatch::matchesVerificationEntry(knownMethodA.data(), exact));
    CHECK(!lcpatch::matchesVerificationEntry(knownMethodB.data(), exact));
    CHECK(lcpatch::matchesVerificationEntry(knownMethodA.data(), masked));
    CHECK(lcpatch::matchesVerificationEntry(knownMethodB.data(), masked));
    CHECK(lcpatch::matchesVerificationEntry(knownMethodA.data(), alternate));

    std::array<uint32_t, 1024> memory{};
    uint8_t *bytes = reinterpret_cast<uint8_t *>(memory.data());
    const uintptr_t base = reinterpret_cast<uintptr_t>(bytes);
    const lcpatch::SignatureScanRange executable[] = {{base, base + sizeof(memory)}};
    auto reader = [](uintptr_t at, uint8_t *dest, size_t len) {
        std::memcpy(dest, reinterpret_cast<const void *>(at), len);
        return true;
    };
    auto scan = [&] { return lcpatch::findUniqueVerificationSignature(
        executable, 1, &masked, 1, reader); };

    auto writeCall = [&](size_t offset, size_t relativeTarget) {
        const uintptr_t site = base + offset + kFileStreamCall;
        const uintptr_t target = base + relativeTarget;
        const int64_t delta = static_cast<int64_t>(target) - static_cast<int64_t>(site);
        CHECK(delta % 4 == 0);
        CHECK(delta >= -(1LL << 27) && delta < (1LL << 27));
        const uint32_t bl = 0x94000000U |
            (static_cast<uint32_t>(delta / 4) & 0x03ffffffU);
        std::memcpy(bytes + offset + kFileStreamCall, &bl, sizeof(bl));
    };
    auto place = [&](size_t offset, const std::array<uint8_t,32> &pattern) {
        std::memcpy(bytes + offset, pattern.data(), pattern.size());
        writeCall(offset, 0xf00); // executable target outside verifier method
    };

    place(0x100, knownMethodA);
    auto match = scan();
    CHECK(match.scanComplete && match.candidateCount == 1);
    CHECK(match.address == base + 0x100 && match.signatureIndex == 0);

    // Relocated function, changed ADRP page immediates and LDRB offset:
    // the new Build ID is unknown but the instruction semantics remain stable.
    std::memset(bytes, 0, sizeof(memory));
    place(0x200, knownMethodB);
    bytes[0x200 + 16] ^= 0x20; // ADRP immediate, not destination register
    bytes[0x200 + 21] ^= 0x80; // second ADRP immediate
    bytes[0x200 + 29] ^= 0x04; // LDRB unsigned offset
    CHECK(lcpatch::matchesVerificationEntry(bytes + 0x200, masked));
    CHECK(!lcpatch::matchesVerificationEntry(bytes + 0x200, exact));
    match = scan();
    CHECK(match.scanComplete && match.candidateCount == 1);
    CHECK(match.address == base + 0x200);
    CHECK(lcpatch::validatesVerificationCandidate(
        executable, 1, base + 0x200, masked, bytes + 0x200, sizeof(memory) - 0x200));

    // Multiple profile templates may match the same candidate: count it once.
    const lcpatch::VerificationSignature variants[] = {masked, alternate};
    match = lcpatch::findUniqueVerificationSignature(executable, 1, variants, 2, reader);
    CHECK(match.scanComplete && match.candidateCount == 1);

    // Changing a masked-out immediate is fine, but changing a register,
    // opcode, or the instruction kind must invalidate the candidate.
    bytes[0x200 + 16] ^= 0x01; // ADRP destination register
    CHECK(!lcpatch::matchesVerificationEntry(bytes + 0x200, masked));
    match = scan();
    CHECK(match.candidateCount == 0 && match.address == 0);
    bytes[0x200 + 16] ^= 0x01;
    bytes[0x200 + 28] ^= 0x01; // LDRB destination register
    CHECK(!lcpatch::matchesVerificationEntry(bytes + 0x200, masked));
    bytes[0x200 + 28] ^= 0x01;

    // A BL-shaped word is insufficient: reject calls into the verifier,
    // outside the executable image, and non-BL instructions.
    writeCall(0x200, 0x300); // inside 0x200 .. 0x3ec
    match = scan();
    CHECK(match.candidateCount == 0 && match.address == 0);
    writeCall(0x200, 0x5000); // outside image
    match = scan();
    CHECK(match.candidateCount == 0 && match.address == 0);
    const uint32_t nop = 0xd503201fU;
    std::memcpy(bytes + 0x200 + kFileStreamCall, &nop, sizeof(nop));
    match = scan();
    CHECK(match.candidateCount == 0 && match.address == 0);

    // Two different valid candidates are ambiguous; never enable redirects.
    std::memset(bytes, 0, sizeof(memory));
    place(0x100, knownMethodA);
    place(0x400, knownMethodB);
    match = scan();
    CHECK(match.candidateCount == 2 && match.address == 0);

    // Method extent must fit the executable segment.
    std::memset(bytes, 0, sizeof(memory));
    place(0xe80, knownMethodA);
    match = scan();
    CHECK(match.scanComplete && match.candidateCount == 0 && match.address == 0);

    match = lcpatch::findUniqueVerificationSignature(
        executable, 1, &masked, 1,
        [](uintptr_t, uint8_t *, size_t) { return false; });
    CHECK(!match.scanComplete && match.address == 0);

    const lcpatch::SignatureScanRange invalid[] = {{0, 100}};
    match = lcpatch::findUniqueVerificationSignature(invalid, 1, &masked, 1, reader);
    CHECK(!match.scanComplete && match.address == 0);

    std::printf("PASS: %u masked-verifier compatibility and safety checks\n", checks);
    return 0;
}
