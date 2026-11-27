# Instrukcja testu: USB Sniffer

Cel testu: sprawdzić, czy kontroler RC-N1C wysyła przez USB czytelne pakiety DUML, czy dane są szyfrowane.

Aplikacja **tylko słucha**. Nie wysyła nic do kontrolera ani do drona, więc nie może niczego uruchomić.

## Bezpieczeństwo (koniecznie)

- Dron stoi na ziemi, najlepiej **ze zdjętymi śmigłami**.
- **Nie ruszaj drążków** w czasie testu. Na Mini 4K pewne ułożenie drążków uruchamia silniki.
- Test robimy w domu lub w ogródku, nie w powietrzu.

## 1. Instalacja aplikacji na Pixelu

1. Na Pixelu otwórz w Chrome stronę z najnowszą wersją:
   `https://github.com/pluszakk/mini4k-open-controller/releases/tag/latest`
2. Pobierz plik `mini4k-usb-sniffer.apk`.
3. Otwórz pobrany plik. Android zapyta o zgodę na instalację z Chrome: zezwól.
4. Na ekranie pojawi się aplikacja „Mini4K USB Sniffer”.

## 2. Test A: zwykłe złącze telefonu (najważniejszy)

1. Zamknij DJI Fly, jeśli jest zainstalowane (przytrzymaj ikonę, „Informacje o aplikacji”, „Wymuś zatrzymanie”).
2. Włącz kontroler i podłącz Pixela kablem, którym normalnie łączysz telefon z kontrolerem.
3. Włącz drona i poczekaj, aż połączy się z kontrolerem (diody przestaną migać).
4. Jeśli Android zapyta, którą aplikacją otworzyć urządzenie, wybierz **Mini4K USB Sniffer** i „Tylko raz”.
5. W aplikacji naciśnij **Start**. Jeśli pojawi się pytanie o dostęp do USB, zezwól.
6. Odczekaj **2 minuty**, nic nie ruszając.
7. Zrób zrzut ekranu aplikacji (przewiń też w dół).
8. Naciśnij **Stop**, potem **Wyślij nagranie** i zapisz plik np. na Dysku Google albo wyślij do siebie mailem.

## 3. Test B: złącze ładowania kontrolera

Ten test sprawdza drugą drogę: telefon jako „komputer” podłączony do gniazda USB-C na spodzie kontrolera.

1. Odłącz kabel telefonu z testu A.
2. Połącz Pixela z gniazdem ładowania kontrolera kablem USB-C – USB-C.
3. Dron i kontroler włączone, jak wcześniej.
4. W aplikacji naciśnij **Start**, zezwól na dostęp do USB.
5. Odczekaj 2 minuty, zrób zrzut ekranu, **Stop**, **Wyślij nagranie**.

## 4. Co mi odesłać

- Oba pliki `capture_....txt` (z testu A i B).
- Zrzuty ekranu.
- Wersję oprogramowania drona i kontrolera, jeśli wiesz, gdzie ją sprawdzić w DJI Fly (nie jest konieczna).

Pliki możesz wrzucić bezpośrednio do rozmowy w aplikacji Claude.

## Co oznacza „Wynik” w aplikacji

| Komunikat | Znaczenie |
| --- | --- |
| czytelny DUML | Najlepszy wynik. Możemy budować dekoder telemetrii. |
| DUML jest, ale część pakietów ma flagę szyfrowania | Częściowo czytelne. Trzeba przeanalizować nagranie. |
| dane nie wyglądają na DUML | Prawdopodobnie szyfrowanie albo inny protokół. |
| kontroler milczy (0 bajtów) | Kontroler czeka na „przywitanie” od aplikacji DJI. Wtedy przechodzimy do planu B na komputerze z Windows. |
