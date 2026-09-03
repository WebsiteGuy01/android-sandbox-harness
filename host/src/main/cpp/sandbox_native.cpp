#include <jni.h>

#include <fcntl.h>
#include <limits.h>
#include <string>
#include <unistd.h>

namespace {

bool Canonicalize(const std::string& input, std::string* output) {
    if (input.empty() || output == nullptr) return false;
    char resolved[PATH_MAX];
    if (realpath(input.c_str(), resolved) == nullptr) return false;
    *output = resolved;
    return true;
}

bool IsDescendant(const std::string& root, const std::string& candidate) {
    std::string normalizedRoot = root;
    while (normalizedRoot.size() > 1 && normalizedRoot.back() == '/') {
        normalizedRoot.pop_back();
    }
    return candidate.size() > normalizedRoot.size() &&
           candidate.compare(0, normalizedRoot.size(), normalizedRoot) == 0 &&
           candidate[normalizedRoot.size()] == '/';
}

void ThrowIllegalArgument(JNIEnv* env, const char* message) {
    jclass type = env->FindClass("java/lang/IllegalArgumentException");
    if (type != nullptr) env->ThrowNew(type, message);
}

}  // namespace

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_host_sandbox_NativePathMapper_openValidated(
    JNIEnv* env,
    jobject,
    jstring sandboxRoot,
    jstring requestedPath,
    jint flags,
    jint mode) {
    if (sandboxRoot == nullptr || requestedPath == nullptr) {
        ThrowIllegalArgument(env, "Path arguments must not be null");
        return -1;
    }

    const char* rootChars = env->GetStringUTFChars(sandboxRoot, nullptr);
    const char* pathChars = env->GetStringUTFChars(requestedPath, nullptr);
    if (rootChars == nullptr || pathChars == nullptr) {
        if (rootChars != nullptr) env->ReleaseStringUTFChars(sandboxRoot, rootChars);
        if (pathChars != nullptr) env->ReleaseStringUTFChars(requestedPath, pathChars);
        return -1;
    }

    const std::string rootInput(rootChars);
    const std::string pathInput(pathChars);
    env->ReleaseStringUTFChars(sandboxRoot, rootChars);
    env->ReleaseStringUTFChars(requestedPath, pathChars);

    std::string root;
    std::string candidate;
    if (!Canonicalize(rootInput, &root) || !Canonicalize(pathInput, &candidate)) {
        return -1;
    }
    if (!IsDescendant(root, candidate)) return -1;

    const int safeFlags = flags | O_CLOEXEC | O_NOFOLLOW;
    const int fd = open(candidate.c_str(), safeFlags, static_cast<mode_t>(mode));
    return fd >= 0 ? fd : -1;
}
