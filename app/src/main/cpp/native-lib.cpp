#include <jni.h>
#include <cstdint>
#include <iomanip>
#include <sstream>
#include <string>

namespace {

uint64_t fnv1a64(const std::string& input) {
    uint64_t hash = 14695981039346656037ULL;
    for (unsigned char c : input) {
        hash ^= static_cast<uint64_t>(c);
        hash *= 1099511628211ULL;
    }
    return hash;
}

uint64_t mix64(uint64_t x) {
    x ^= x >> 30;
    x *= 0xbf58476d1ce4e5b9ULL;
    x ^= x >> 27;
    x *= 0x94d049bb133111ebULL;
    x ^= x >> 31;
    return x;
}

std::string fingerprint(const std::string& input) {
    const uint64_t primary = fnv1a64(input);
    const uint64_t secondary = mix64(primary ^ static_cast<uint64_t>(input.size()));

    std::ostringstream out;
    out << input.size()
        << ":"
        << std::hex
        << std::setw(16)
        << std::setfill('0')
        << primary
        << std::setw(16)
        << std::setfill('0')
        << secondary;

    return out.str();
}

} // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_com_bittv_iptv_util_NativePlaylist_fingerprint(
    JNIEnv* env,
    jclass,
    jstring text
) {
    if (text == nullptr) {
        return env->NewStringUTF("");
    }

    const char* chars = env->GetStringUTFChars(text, nullptr);
    if (chars == nullptr) {
        return env->NewStringUTF("");
    }

    const std::string input(chars);
    env->ReleaseStringUTFChars(text, chars);

    const std::string result = fingerprint(input);
    return env->NewStringUTF(result.c_str());
}
