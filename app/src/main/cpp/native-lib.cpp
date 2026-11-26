#include <jni.h>

#include "cdiscord.h"

#include <string>
#include <android/log.h>

// Instância única do cliente Discord
static Discord_Client discordClient{};
static bool discordInitialized = false;

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    return JNI_VERSION_1_6;
}

// Inicializa o cliente Discord
JNIEXPORT jboolean JNICALL
Java_com_cldev_presence_MonitoringService_nativeInitializeDiscord(
        JNIEnv* env,
        jobject thiz) {

    if (discordInitialized) {
        return JNI_TRUE;
    }

    Discord_Client_Init(&discordClient);

    Discord_Client_SetApplicationId(
            &discordClient,
            1575222100153864323ULL
    );

    discordInitialized = true;

    return JNI_TRUE;
}

// Processa os callbacks do SDK
JNIEXPORT void JNICALL
Java_com_cldev_presence_MonitoringService_nativeRunDiscordCallbacks(
        JNIEnv* env,
        jobject thiz) {

    if (discordInitialized) {
        Discord_RunCallbacks();
    }
}

// Encerra o cliente Discord
JNIEXPORT void JNICALL
Java_com_cldev_presence_MonitoringService_nativeShutdownDiscord(
        JNIEnv* env,
        jobject thiz) {

    if (discordInitialized) {
        Discord_Client_Drop(&discordClient);
        discordInitialized = false;
    }


}

// Função auxiliar: converte texto C++ para Discord_String
static Discord_String toDiscordString(const std::string& value) {
    Discord_String result{};
    result.ptr = reinterpret_cast<uint8_t*>(
            const_cast<char*>(value.data())
    );
    result.size = value.size();
    return result;
}

// Liberação segura de dados de callback
static void freeCallbackData(void* userData) {
    // Nenhum dado adicional foi alocado.
}

// Atualiza a presença do Discord
JNIEXPORT void JNICALL
Java_com_cldev_presence_MonitoringService_nativeUpdateRichPresence(
        JNIEnv* env,
        jobject thiz,
        jstring gameTitle,
        jstring titleId,
        jstring coverUrl) {

    if (!discordInitialized) {
        return;
    }

    const char* titleChars = env->GetStringUTFChars(gameTitle, nullptr);
    const char* idChars = env->GetStringUTFChars(titleId, nullptr);
    const char* coverChars = env->GetStringUTFChars(coverUrl, nullptr);

    if (titleChars == nullptr || idChars == nullptr || coverChars == nullptr) {
        if (titleChars != nullptr)
            env->ReleaseStringUTFChars(gameTitle, titleChars);

        if (idChars != nullptr)
            env->ReleaseStringUTFChars(titleId, idChars);

        if (coverChars != nullptr)
            env->ReleaseStringUTFChars(coverUrl, coverChars);

        return;
    }

    std::string title(titleChars);
    std::string id(idChars);
    std::string cover(coverChars);

    env->ReleaseStringUTFChars(gameTitle, titleChars);
    env->ReleaseStringUTFChars(titleId, idChars);
    env->ReleaseStringUTFChars(coverUrl, coverChars);

    Discord_Activity activity{};
    Discord_Activity_Init(&activity);

    // Nome principal da atividade
    Discord_Activity_SetName(
            &activity,
            toDiscordString(title)
    );

    Discord_Activity_SetType(
            &activity,
            Discord_ActivityTypes_Playing
    );

    // Informações exibidas abaixo do nome
    std::string detailsStr = "Jogando no PlayStation 3";
    Discord_String details = toDiscordString(detailsStr);

    Discord_String state = toDiscordString(id);

    Discord_Activity_SetDetails(&activity, &details);
    Discord_Activity_SetState(&activity, &state);



// Mantém os recursos vivos durante o envio
    Discord_ActivityAssets assets{};
    bool hasAssets = false;

    Discord_String imageUrl{};
    Discord_String imageText{};

// Usa a capa recebida da TheGamesDB
    if (!cover.empty()) {

        Discord_ActivityAssets_Init(&assets);
        hasAssets = true;

        imageUrl = toDiscordString(cover);
        imageText = toDiscordString(title);

        Discord_ActivityAssets_SetLargeImage(
                &assets,
                &imageUrl
        );

        Discord_ActivityAssets_SetLargeText(
                &assets,
                &imageText
        );

        Discord_Activity_SetAssets(&activity, &assets);
    }

// Envia a presença antes de liberar os recursos
    Discord_Client_UpdateRichPresence(
            &discordClient,
            &activity,
            nullptr,
            freeCallbackData,
            nullptr
    );

// Libera os recursos somente depois do envio
    if (hasAssets) {
        Discord_ActivityAssets_Drop(&assets);
    }

    Discord_Activity_Drop(&activity);

}

// Limpa a presença do Discord
JNIEXPORT void JNICALL
Java_com_cldev_presence_MonitoringService_nativeClearRichPresence(
        JNIEnv* env,
        jobject thiz) {

    if (discordInitialized) {
        Discord_Client_ClearRichPresence(&discordClient);
    }
}
}