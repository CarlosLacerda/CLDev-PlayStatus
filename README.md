# CLDev PlayStatus

 <p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/Gradle-8.9-02303A?logo=gradle&logoColor=white" />
  <img src="https://img.shields.io/badge/CMake-4.1.2-064F8C?logo=cmake&logoColor=white" />
  <img src="https://img.shields.io/badge/NDK-27.2-3DDC84?logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Android_API-26%2B-3DDC84?logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Architecture-ARM64-blue" />
  <img src="https://img.shields.io/badge/Status-In_Development-orange" />
</p>

Aplicativo Android que conecta o PlayStation 3 ao Discord, permitindo exibir automaticamente o jogo em execução no perfil do usuário por meio do **Discord Rich Presence**.

O projeto utiliza o WebMAN MOD para identificar os jogos no PS3, a TheGamesDB para recuperar informações e capas e o Discord Social SDK para publicar a atividade no Discord.

**Repositório:** https://github.com/CarlosLacerda/CLDev-PlayStatus

## Funcionalidades

- **Detecção de jogos:** comunicação local com o WebMAN MOD por meio do endpoint `/cpursx.ps3`.
- **Identificação:** leitura do título do jogo e do Title ID.
- **Monitoramento automático:** verificação periódica do PS3 a cada 10 segundos.
- **Execução em segundo plano:** serviço em primeiro plano no Android, com notificação persistente.
- **Integração com TheGamesDB:** consulta de metadados, incluindo título, data de lançamento e capa.
- **Biblioteca de jogos:** exibição dos jogos identificados, com capas e informações.
- **Discord Rich Presence:** publicação dinâmica do jogo em execução, detalhes do PlayStation 3, Title ID e imagem correspondente.
- **Controle manual:** opções para iniciar e interromper o monitoramento.

## Tecnologias utilizadas

| Tecnologia | Finalidade |
|---|---|
| Kotlin | Desenvolvimento Android |
| Jetpack Compose | Interface do aplicativo |
| Android SDK | Plataforma Android |
| Gradle | Automação da compilação |
| C++ / JNI | Comunicação entre Kotlin e código nativo |
| CMake / Android NDK | Compilação nativa |
| Discord Social SDK | Integração com Discord Rich Presence |
| WebMAN MOD | Comunicação com o PS3 |
| TheGamesDB API | Metadados e capas dos jogos |
| Coil | Carregamento de imagens |

## Ambiente de desenvolvimento

Configuração utilizada no desenvolvimento:

- Android Studio
- JDK 17 ou superior
- Android SDK Platform 35
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21
- Gradle 8.9
- Android NDK 27.2.12479018
- CMake 4.1.2
- Arquitetura nativa: ARM64 (`arm64-v8a`)

## Dispositivos e ambiente de teste

- Motorola Edge 30 Fusion
- Android 14
- PlayStation 3 Super Slim
- Firmware 4.92 com HEN
- WebMAN MOD
- Conexão entre PS3 e Android pela mesma rede Wi-Fi

## Configuração do projeto

### 1. Clonar o repositório

```bash
git clone https://github.com/CarlosLacerda/CLDev-PlayStatus.git
```

Abra a pasta do projeto no Android Studio e aguarde a sincronização do Gradle.

### 2. Configurar o Discord Social SDK

O SDK da Discord não é distribuído junto com este repositório.

Obtenha a versão compatível pelos canais oficiais da Discord, respeitando os requisitos de acesso e os termos de utilização.

O projeto foi desenvolvido com o Discord Social SDK versão `1.11.24259`.

Coloque o arquivo AAR no seguinte caminho:

```
app/libs/discord_partner_sdk.aar
```

O AAR fornece a biblioteca nativa e os cabeçalhos utilizados pela integração CMake/Prefab.

### 3. Configurar a chave da TheGamesDB

Obtenha uma chave de API da TheGamesDB.

No Windows, configure a propriedade no arquivo global do Gradle:

```
%USERPROFILE%\.gradle\gradle.properties
```

Adicione:

```
THEGAMESDB_API_KEY=SUA_CHAVE_AQUI
```

Não coloque a chave no `gradle.properties` do projeto nem a envie ao GitHub.

**Atenção:** a chave é incorporada ao aplicativo por meio do BuildConfig. Portanto, essa configuração evita expô-la no repositório, mas não impede sua extração de um APK distribuído.

### 4. Configurar o PS3

1. Ligue o PlayStation 3 e ative o HEN.
2. Confirme que o WebMAN MOD está carregado.
3. Conecte o PS3 e o Android à mesma rede Wi-Fi.
4. Consulte o endereço IP do console em Configurações > Configurações de rede > Lista de estado da ligação.
5. Informe o IP do PS3 no aplicativo.

O aplicativo consulta:

```
http://IP_DO_PS3:80/cpursx.ps3
```

O endpoint é utilizado para obter informações do console e identificar o jogo em execução.

Referência: [WebMAN MOD – Web Commands](https://github.com/aldostools/webMAN-MOD/wiki/Web-Commands).

## Compilação

Depois de configurar o SDK da Discord e a chave da TheGamesDB, execute na raiz do projeto:

**Windows:**

```
.\gradlew.bat assembleDebug
```

O APK de depuração será gerado em:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Estrutura simplificada

```
CLDev-PlayStatus/
├── app/
│   ├── libs/
│   │   └── discord_partner_sdk.aar
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       └── cpp/
│   │           └── native-lib.cpp
│   └── CMakeLists.txt
├── gradle/
├── .gitignore
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── gradlew
└── gradlew.bat
```

O arquivo AAR é uma dependência local e não faz parte do repositório.

## Avisos

- O projeto é independente e não é um produto oficial da Discord, da Sony ou da TheGamesDB.
- O Discord Social SDK possui requisitos próprios de acesso e utilização.
- Não redistribua arquivos proprietários do SDK sem autorização.
- O aplicativo depende de uma conexão de rede entre o PS3 e o dispositivo Android.
- A compatibilidade nativa atual está configurada para ARM64 (`arm64-v8a`).

## Status do projeto

**Desenvolvimento em andamento.**

O projeto já possui comunicação com o WebMAN MOD, identificação de jogos, consulta de metadados e integração funcional com Discord Rich Presence.

Desenvolvido por **CLDev**.
