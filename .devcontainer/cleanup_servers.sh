#!/bin/bash
    
# Список имен процессов, которые нужно завершить.
# Добавьте сюда имена ваших серверов, например, "node", "python", "dotnet" и т.д.
PROCESSES_TO_KILL=("java" "node")
    
echo "Скрипт очистки запущен. Завершение серверов через 10 секунд..."
    
# Ждем 10 секунд
sleep 10
    
# Завершаем процессы
for process_name in "${PROCESSES_TO_KILL[@]}"; do
    if pgrep -x "$process_name" > /dev/null; then
        echo "Завершаю все процессы с именем '$process_name'..."
        killall "$process_name"
    else
        echo "Процессы с именем '$process_name' не найдены."
    fi
done
    
echo "Очистка завершена."
