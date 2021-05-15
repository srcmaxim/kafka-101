curl -i -X POST -H "Content-Type:application/json" \
  http://localhost:8083/connectors \
  -d @elasticsearch_connector_sink.json
