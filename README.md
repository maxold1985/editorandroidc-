# EditorAndroidC++

Editor C/C++ em Java para Android com realce de sintaxe, seletor SAF de projetos, botões CMake/Make/Executar e console integrado ao Termux.

## Compiladores GCC/G++ e Clang

Agora o editor tem um seletor **GCC / G++** ou **Clang / Clang++**, usado em compilação direta e CMake/Make. O botão **Compiladores** mostra os caminhos reais de `gcc`, `g++`, `clang`, `clang++` e suas versões. No Termux oficial, `gcc` e `g++` costumam ser links para o Clang, não uma instalação do GCC GNU.

- **Compilar C/C++**: salva/sincroniza o arquivo selecionado (`.c`, `.cpp`, `.cc`, `.cxx`) e compila diretamente com `gcc -std=c11` ou `g++ -std=c++17` (ou Clang selecionado).
- **CMake**: passa `-DCMAKE_C_COMPILER` e `-DCMAKE_CXX_COMPILER` apontando aos comandos detectados.
- **Make**: reconfigura o CMake e compila o projeto.
- **Executar**: inicia o executável em `~/editorandroidc-/bin/`.
- **Pastas de build separadas**: `~/editorandroidc-/build-gcc` e `~/editorandroidc-/build-clang`.

Para verificar no Termux:

```bash
pkg update
pkg install clang cmake make
command -v gcc
command -v g++
gcc --version
g++ --version
```

GCC GNU verdadeiro exige uma distribuição/container ou toolchain GCC adicional; aliases Clang não substituem GCC GNU.

## Prepare o Termux

```bash
pkg update
pkg install clang cmake make
termux-setup-storage
mkdir -p ~/.termux
sed -i '/^allow-external-apps=/d' ~/.termux/termux.properties
printf '\nallow-external-apps=true\n' >> ~/.termux/termux.properties
termux-reload-settings
```

Instale Termux >= 0.109 e conceda ao editor a permissão **Run commands in Termux environment**. Escolha uma subpasta em `Documents` ou `Download` no armazenamento interno pelo botão **Pasta**. O Termux deve ter permissão de acesso aos arquivos compartilhados.

Pasta de fontes dentro do Termux: `~/editorandroidc-/source`
Pasta de compilação: `~/editorandroidc-/build`
Binário: `~/editorandroidc-/bin/editor_sample`

**CMake** configura o projeto. **Make** importa, configura e compila. **Executar** roda o executável selecionado. Para outro nome de target, altere o campo superior.

O editor usa `RUN_COMMAND` do Termux e mostra `stdout`, `stderr` e `exitCode` quando o comando finaliza. O aplicativo não contém compilador próprio.

## Compilar APK
Abra o projeto com Android Studio, JDK 17 e SDK 35, ou execute o workflow **Android APK** na aba Actions. O artefato fica em `app/build/outputs/apk/debug/app-debug.apk`.

O projeto usa Gradle 8.9 (wrapper JAR não incluso). A CI instala essa versão diretamente.

Documentação: https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
