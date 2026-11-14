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
    std::cout << "TextAsset allowlist, collision and traversal checks passed\n";
}
