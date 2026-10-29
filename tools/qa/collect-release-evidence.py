"""Collect non-sensitive QA reports; never publish a campaign save or auth file."""
from pathlib import Path
import json,hashlib,xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[2]
evidence=root/'artifacts/release070'
out=root/'docs/qa/0.7.0';out.mkdir(parents=True,exist_ok=True)
def report(name,source):
    data=json.loads((evidence/source).read_text(encoding='utf-8-sig'))
    if 'passed' in data:assert data['passed'],source
    if 'checkpoint' in data and data['checkpoint']:data['checkpoint']='Проверочное сохранение после главы V из успешного прохождения за троих'
    if 'sourceCheckpoint' in data:data['sourceCheckpoint']='Сохранение проигранной партии второй главы: два Эхо, один живой разведчик'
    (out/(name+'.json')).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return data

trio=report('fresh-trio','fresh3-4x/full-campaign-result.json')
duo=report('fresh-medic-scout','fresh-duo12/full-campaign-result.json')
branches={r:report('final-'+r,'route-'+r+'/full-campaign-result.json') for r in ['bastion','cleansing','symbiosis','protocol','awakening']}
echo=report('echo-commands','echo-native/echo-result.json')
minute=report('packaged-minute','packaged-smoke/qa-result.json')
net3=report('network-trio','network-active-trio.json')
net2=report('network-medic-scout','network-active-duo.json')
# The pre-transition local object is mutable in the early helper version; do not publish its mislabeled sourceAct.
net3.pop('sourceAct',None)
(out/'network-trio.json').write_text(json.dumps(net3,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
totals={k:0 for k in ['tests','failures','errors','skipped']};suites=[]
for module in ['shared-model','game-server','game-client']:
    group={k:0 for k in totals}
    for file in (root/module/'target/surefire-reports').glob('TEST-*.xml'):
        attrs=ET.parse(file).getroot().attrib
        for k in group:group[k]+=int(attrs[k])
    suites.append({'module':module,**group})
    for k in totals:totals[k]+=group[k]
assert totals['tests']==157 and totals['failures']==totals['errors']==totals['skipped']==0
(out/'automated.json').write_text(json.dumps({'passed':True,**totals,'modules':suites},indent=2)+'\n',encoding='utf-8')
jarfiles=[root/'game-client/target/game-client-0.7.0.jar',root/'game-server/target/game-server-0.7.0.jar',root/'shared-model/target/shared-model-0.7.0.jar']
jarhashes={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in jarfiles}
(out/'tested-jars.json').write_text(json.dumps(jarhashes,indent=2)+'\n',encoding='utf-8')
error=(evidence/'fresh3/walkthrough-error.txt').read_text(encoding='utf-8-sig').strip()
(out/'failed-attempt.json').write_text(json.dumps({'passed':False,'players':3,'qaSpeed':8,'reachedAct':5,'outcome':'Падение','detail':error,'countedAsSuccessfulCompletion':False,'repeatSuccess':'fresh-trio.json'},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
labels={'bastion':'Оплот','cleansing':'Очищение','symbiosis':'Симбиоз','protocol':'Новый протокол','awakening':'Истинное пробуждение'}
rows='\n'.join('| '+labels[r]+' | '+str(v['commands'])+' | '+str(v['echoSupportCommands'])+' | 3/3 |' for r,v in branches.items())
installer_note='Результат проверки MSI добавляется в публичный отчёт после упаковки. Проверка содержимого и установка/удаление различаются.'
if (out/'installer-payload.json').is_file():
    installer=json.loads((out/'installer-payload.json').read_text(encoding='utf-8'));assert installer['passed']
    installer_note=f"MSI версии 0.7.0 распакован WiX dark: **{installer['matchedApplicationFiles']} файла приложения совпали с проверенным app-image**, включая EXE, Java, игровые JAR и PDF. Служебный маркер установщика проверен отдельно, всего {installer['packagedFiles']} файла. [Отчёт MSI](qa/0.7.0/installer-payload.json). Реальная установка/удаление этой версии не выполнены: защитная проверка обнаружила связанную установленную игру и завершилась до её изменения. Нативный запуск app-image проверен отдельно."
doc=f'''# Проверки 0.7.0 — 29.10.2026

Публичная техническая альфа. Прогоны используют настоящие TCP-соединения с авторитетным сервером. Управляющий сценарий отдаёт обычные команды: он не телепортирует героев, не выдаёт предметы, не удаляет существ и не выставляет сюжетные предпосылки. Все сохранения и снимки изолированы от пользовательского профиля.

## Автоматические проверки

`mvnw -o -B clean verify`, затем окончательный `verify`: **{totals['tests']} проверок, 0 ошибок, 0 пропусков** (модель 4, сервер 130, клиент 23). Проверяются навигация, предметы, последствия, двери, медицина, оружие, этажи, сроки, ключи, личное согласие, шесть маршрутов, Эхо, сохранения, TCP, ограничение распаковки, очереди, переподключение и JavaFX.

Новые проверки подтверждают реальные обработчики кнопок меню, отсутствие исходного HUD-референса в ресурсах JAR, правильный размер подписи и сохранение динамического цвета статуса. [Машинный отчёт](qa/0.7.0/automated.json).

## Полные прохождения I–VI с нуля

| Состав | Маршрут | Команды | Реальное время | Итог |
|---|---|---:|---:|---|
| Трое, ускорение ×4 | Исход | {trio['commands']} | {trio['wallSeconds']:.1f} с | инженер и разведчик эвакуированы, медик Эхо; связь 3/3 |
| Медик + разведчик, ×8 | Исход | {duo['commands']} | {duo['wallSeconds']:.1f} с | оба эвакуированы, инженер в капсуле; связь 2/2 |

Проверены получение и анализ образца, изменения после голосования, реальная подготовка выхода и личная эвакуация, переходы глав, затопление и лестницы, архивы, фракции, клиника и финальные работы. Существа остаются в текущей карте и архивах посещённых локаций. В прогоне за троих приняты {trio['echoSupportCommands']} команды полевой поддержки Эхо. [Трое](qa/0.7.0/fresh-trio.json), [ваша пара персонажей](qa/0.7.0/fresh-medic-scout.json).

Первая попытка за троих на ×8 закончилась «Падением» в V главе; она сохранена как [неуспешная](qa/0.7.0/failed-attempt.json) и не учитывается как пройденная. Ошибочные ранние запуски без правильного dev-флага также не засчитываются.

## Пять альтернативных финалов

Проверены на **JAR и runtime собранного Windows-приложения**, из настоящего сохранения после V главы успешной тройки. Ранние главы повторно не проходились. Три клиента, два полевых тела и медик Эхо, ускорение ×8. Все пять веток закончились выбранным эпилогом и эвакуацией оставшихся живых.

| Маршрут | Команды | Поддержка Эхо | Связь в финале |
|---|---:|---:|---:|
{rows}

Проверены две разные подписи пультов, физический якорь, термоизоляция без убийства существ, автономный терминал, личная настройка и остановка циклов. [JSON финалов](qa/0.7.0/).

## Сеть и Эхо

- Трое, активная VI глава, 120 с, ограничение разведчика 32 КиБ/с и пауза чтения 10 с: **{net3['commandsAcknowledged']}/{net3['commandsSent']} подтверждений**, связь 3/3, циклы {net3['cycles']}. [Отчёт](qa/0.7.0/network-trio.json).
- Медик + разведчик, активная VI глава, 120 с, 64 КиБ/с и та же пауза: **{net2['commandsAcknowledged']}/{net2['commandsSent']} подтверждений**, связь 2/2. [Отчёт](qa/0.7.0/network-medic-scout.json).
- Отдельное окно Эхо: {echo['commands']} TCP-команд, камера, общая отметка всем клиентам, повтор commandId без повторной цены, реальный шум, обычный шлюз и истечение данных. Питание {echo['powerBefore']:.0f} → {echo['powerAfter']:.0f}; тела остаются потерянными. [Отчёт](qa/0.7.0/echo-commands.json).

Большой возраст снимка и задержка команды во время намеренной паузы ожидаемы. Предупреждения EOF в конце сетевых журналов возникают после закрытия тестового прокси; итоговые подтверждения и состояние соединений фиксируются до закрытия.

## Windows, графика и руководство

- Нативный `HIVE-Protocol13.exe` запущен со встроенной Java, при убранных JAVA_HOME и Java из PATH. Фаза длилась **{minute['realMinuteSeconds']:.3f} с**, три клиента двигались, голосование остановило мир, изоляция B4 списала ресурс. [Отчёт](qa/0.7.0/packaged-minute.json).
- Три игровых JAR совпадают с проверенными сборками побайтно: [SHA-256](qa/0.7.0/tested-jars.json).
- Руководство: 18 страниц, встроенный кириллический шрифт, все страницы отрисованы и просмотрены; обложка и финальные иллюстрации перепроверены после правки. HTML работает локально, снимки открываются в полном размере.
- Значок включён в Windows-приложение. Подлинные портреты/фон проекта заменяют вырезки из пользовательских референсов; сырые референсы не входят в JAR.

{installer_note}

## Не проверено и не завершено

Это не тест трёх физических ПК, реального VPN, долговременной потери пакетов или кампании 6–10 часов. Полный набор из восьми поведений существ, 80+ событий, глубина социальных веток и вся визуальная приёмка исходного ТЗ остаются открытыми. Финальные маршруты реализованы и проверены в указанном объёме, но это не означает, что весь проект принят как версия 1.0. [Статус](STATUS.md).
'''
(root/'docs/QA_0_7_0_RU.md').write_text(doc,encoding='utf-8')
print(json.dumps({'automated':totals,'freshRuns':2,'checkpointFinals':len(branches),'reports':len(list(out.glob('*.json')))},ensure_ascii=False))
