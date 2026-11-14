#include "../text_asset_paths.h"
#include <cassert>
#include <iostream>

int main() {
    assert(lcpatch::assetKey("JP_LoginUIText.json") == "LoginUIText");
    assert(lcpatch::assetKey("FileDownloadDesc") == "FileDownloadDesc");
    assert(lcpatch::safeAssetPath("StoryData/JP_S1.json"));
    for (const char *bad : {"../JP_X.json", "/JP_X.json", "a/../x.json", "a//x.json", "C:x.json", "a\\x.json", "x.tsv"})
        assert(!lcpatch::safeAssetPath(bad));
    const auto index = lcpatch::parseAssetIndex(
        "LoginUIText\tJP_LoginUIText.json\nBad\t../bad.json\nCommon\ta/JP_Common.json\nCommon\tb/JP_Common.json\nCommon\ta/JP_Common.json\n");
    assert(index.size() == 1);
    assert(index.at("LoginUIText") == "JP_LoginUIText.json");
    uintptr_t branchTarget = 0;
    // Identified 1.117.1 retained code: public Resolve export tail-calls the
    // same implementation that generated icall wrappers call directly.
    assert(lcpatch::aarch64TailBranchTarget(0x4570b7cU, 0x14010ccdU, branchTarget));
    assert(branchTarget == 0x45b3eb0U);
    assert(lcpatch::aarch64TailBranchTarget(0x1000U, 0x17ffffffU, branchTarget));
    assert(branchTarget == 0xffcU);
    assert(!lcpatch::aarch64TailBranchTarget(0x1000U, 0x94000004U, branchTarget)); // BL
    assert(!lcpatch::aarch64TailBranchTarget(0x1000U, 0x54000080U, branchTarget)); // B.cond
    assert(!lcpatch::aarch64TailBranchTarget(0x1000U, 0U, branchTarget)); // protected/unready page
    assert(!lcpatch::aarch64TailBranchTarget(0x1000U, 0x14000000U, branchTarget)); // self loop
    assert(!lcpatch::aarch64TailBranchTarget(0x1001U, 0x14000001U, branchTarget));
    assert(!lcpatch::aarch64TailBranchTarget(0U, 0x17ffffffU, branchTarget)); // underflow
    assert(!lcpatch::aarch64TailBranchTarget(UINTPTR_MAX - 3U, 0x14000001U, branchTarget));
    assert(lcpatch::aarch64BranchLandingHint(0xd503245fU));
    assert(!lcpatch::aarch64BranchLandingHint(0xd503233fU)); // PACIASP is not a veneer hint
    std::unordered_map<uintptr_t, uint32_t> code{{0x1000U, 0x14000040U}, {0x1100U, 0xd10183ffU}};
    const auto read = [&code](uintptr_t address, uint32_t &instruction) {
        const auto found = code.find(address);
        if (found == code.end()) return false;
        instruction = found->second;
        return true;
    };
    assert(lcpatch::findSharedIcallResolver(0x1000U, read).entry == 0x1100U);
    code[0x1100U] = 0;
    assert(lcpatch::findSharedIcallResolver(0x1000U, read).pending);
    code.erase(0x1100U);
    assert(std::string(lcpatch::findSharedIcallResolver(0x1000U, read).reason) == "resolver_branch_outside_image");
    code[0x1000U] = 0xd10183ffU;
    assert(std::string(lcpatch::findSharedIcallResolver(0x1000U, read).reason) == "public_resolver_not_tail_veneer");
    code[0x1000U] = 0xd503245fU;
    code[0x1004U] = 0x1400003fU;
    code[0x1100U] = 0xd10183ffU;
    assert(lcpatch::findSharedIcallResolver(0x1000U, read).entry == 0x1100U);
    code[0x1100U] = 0x17ffffc0U; // B back to 0x1000
    assert(std::string(lcpatch::findSharedIcallResolver(0x1000U, read).reason) == "resolver_veneer_cycle");
    std::unordered_map<uintptr_t, uint32_t> wrapper{{0x2000U,0x94000040U},{0x2004U,0xd65f03c0U},{0x2100U,0xd61f0020U}};
    const auto wrapperRead = [&wrapper](uintptr_t at, uint32_t &word) {
        const auto i = wrapper.find(at); if (i == wrapper.end()) return false; word = i->second; return true;
    };
    assert(lcpatch::findTextBytesReturn(0x2000U,0x2100U,wrapperRead) == 0x2004U);
    wrapper[0x2100U] = 0xd65f03c0U;
    assert(!lcpatch::findTextBytesReturn(0x2000U,0x2100U,wrapperRead)); // normal return
    wrapper[0x2100U] = 0xa9bf7bfdU; wrapper[0x2104U] = 0x14000001U; wrapper[0x2108U] = 0xd61f0020U;
    assert(!lcpatch::findTextBytesReturn(0x2000U,0x2100U,wrapperRead)); // cannot scan past function tail
    wrapper[0x2100U] = 0xd61f03c0U;
    assert(!lcpatch::findTextBytesReturn(0x2000U,0x2100U,wrapperRead)); // BR LR is not native dispatch
    wrapper[0x2100U] = 0xd61f0020U; wrapper[0x2004U] = 0x9400003fU; wrapper[0x2008U] = 0xd65f03c0U;
    assert(!lcpatch::findTextBytesReturn(0x2000U,0x2100U,wrapperRead)); // ambiguous calls
    wrapper[0x2000U] = 0x14000040U;
    assert(!lcpatch::findTextBytesReturn(0x2000U,0x2100U,wrapperRead)); // text tail branch is unsupported
    wrapper[0x2000U] = 0;
    assert(!lcpatch::findTextBytesReturn(0x2000U,0x2100U,wrapperRead)); // protected code
    assert(!lcpatch::findTextBytesReturn(0x2001U,0x2100U,wrapperRead));
    wrapper[0x2200U] = 0x97ffffc0U; wrapper[0x2204U] = 0xd65f03c0U;
    assert(lcpatch::findTextBytesReturn(0x2200U,0x2100U,wrapperRead) == 0x2204U); // signed backward BL
    wrapper[0x2300U] = 0x17ffffc0U; // B to text body at0x2200
    assert(lcpatch::findTextBytesReturn(0x2300U,0x2100U,wrapperRead) == 0x2204U);
    wrapper[0x2300U] = 0x14000001U; wrapper[0x2304U] = 0x17ffffffU;
    assert(!lcpatch::findTextBytesReturn(0x2300U,0x2100U,wrapperRead)); // veneer cycle
    assert(lcpatch::textAssetDisplayResource("LoginUIText"));
    assert(lcpatch::textAssetDisplayResource("BattleHint_NormalBattle"));
    assert(!lcpatch::textAssetDisplayResource("UserAgreements"));
    assert(!lcpatch::textAssetDisplayResource("LocalizeFileList"));
    std::cout << "TextAsset allowlist, traversal, resolver veneer and text-only bytes caller checks passed\n";
}
