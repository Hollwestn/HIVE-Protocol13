# Сборка Windows

Требования: Windows x64, JDK 25, Maven Wrapper в репозитории. Для EXE/MSI нужен WiX 3.x в PATH. JavaFX 25.0.2 и зависимости загружаются Maven; версии закреплены в POM. В текущей среде использованы JDK 25.0.3, Maven 3.9.12, WiX 3.11. Для автономного runtime — Temurin 25.0.4.1+1.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.3'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd -B verify
powershell -ExecutionPolicy Bypass -File tools/Run-Game.ps1
```

Maven verify включает локальные TCP-тесты. Ограниченная песочница может запретить socket connect; тогда запускать в обычном Windows-терминале. Отдельно установленные Java 8/17 в PATH не подходят; JAVA_HOME должен указывать на 25.

```powershell
./tools/Build-Windows.ps1 -RuntimePath 'C:\path\to\OpenJDK25-jre'
```

Скрипт создаёт app-image, EXE, MSI и Portable ZIP с иконкой и офлайн-руководством docs/guide. Runtime должен быть Windows x64 Java 25, с сохранёнными legal/notice-файлами. Артефакты `release/HIVE-Protocol13-Setup.exe`, `.msi`, `Portable.zip`; SHA-256 — `release/SHA256SUMS.txt`. Текущая версия: техническая альфа 0.7.0. Существующий каталог сборки не удаляется; для повторения используется новый BuildId.

После исправления причины ошибки упаковки можно продолжить существующий app-image через `-Resume -BuildId ИМЯ -SkipVerify`; изменённые исходники требуют новой сборки с новым BuildId. WiX создаёт EXE с атрибутом ReadOnly, поэтому публикация собственных обновлённых артефактов выполняется Copy-Item -Force. Тест установки `tools/Test-WindowsInstaller.ps1` отказывается затрагивать существующие связанные установки, устанавливает MSI в каталог artifacts, запускает минутный QA со встроенной Java и удаляет тестовую установку. Это отдельная реальная операция Windows Installer, а не часть обычного Maven verify.

Воспроизводимое минутное QA-прохождение (3 отдельных TCP-соединения, тестовые управляющие клиенты явно включены флагом):

```powershell
& "$env:JAVA_HOME\bin\java.exe" --enable-native-access=ALL-UNNAMED '-Dhive.mute=true' '-Dhive.dataDir=artifacts/test-data/local-run' -jar game-client/target/game-client-0.7.0.jar --qa=true --capture=artifacts/local-run --width=1536 --height=864 --record=true
```

Для повторного запуска укажите новый каталог capture: файл сетевых идентификаторов предыдущего теста резервирует роли. По умолчанию тест длится около 70 секунд, а не ускоренную «минуту». Три управляющих клиента подключены к настоящему сокетному серверу; они не являются тремя физическими ПК. Результат в qa-result.json, собственные снимки GOLDEN_MAIN_A/VOTE/RESOLVED. Запись реального UI:

```powershell
python tools/make-demo.py artifacts/local-run release/REFERENCE_DEMO_ACTUAL.mp4
```

Нужен ffmpeg в PATH. При пропусках захвата сохраняется исходное время по номерам кадров. Исходный MP4 из references для этого не используется.
