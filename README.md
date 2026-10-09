# EditorAndroidC++

Editor C/C++ em Java para Android com realce de sintaxe, seletor SAF de projetos, botões CMake/Make/Executar e console integrado ao Termux.

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
