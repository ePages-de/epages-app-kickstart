curl -X PUT -H "Content-Type: application/json" http://localhost:8088/rs/oauth2/import/clients --data @import-client-payload.json
curl -X PUT -H "Content-Type: application/json" http://localhost:8088/rs/appstore/import/apps --data @import-app-payload.json
