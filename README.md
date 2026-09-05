# NotToday-mobile

Flutter 앱 (`nottoday_mobile` 1.0.0) — 로컬 SQLite 엔진(`database/`) 기반.

## 구성

- `lib/` — Flutter 앱 (`main.dart` + screens/services/widgets)
- `android/` — Android 네이티브
- `database/` — 로컬 SQLite (`schema.sql`, `local_sqlite_engine.py`)
- `docker-compose.yml` — 보조 인프라 (Neo4j, Redis)

## 실행

```bash
flutter pub get
flutter run
```

보조 인프라가 필요하면:

```bash
docker compose up -d   # Neo4j(:7474/:7687), Redis(:6379)
```

## 주의

- `docker-compose.yml`의 `cloud-gateway`는 `./server/` 미구현으로 비활성화됨. 구현 후 주석 해제할 것.
- Twilio 등 외부 키는 환경변수로만 전달하고 커밋하지 않는다.
