docker stop mongo-event-sourcing 2>/dev/null || true
docker rm mongo-event-sourcing 2>/dev/null || true

rm -f /tmp/mongo-keyfile
openssl rand -base64 756 > /tmp/mongo-keyfile
chmod 600 /tmp/mongo-keyfile
sudo chown 999:999 /tmp/mongo-keyfile

docker run -d --name mongo-event-sourcing \
  -p 27020:27017 \
  -v mongo_event_sourcing_data:/data/db \
  -v /tmp/mongo-keyfile:/etc/mongo-keyfile:ro \
  -e MONGO_INITDB_ROOT_USERNAME=admin \
  -e MONGO_INITDB_ROOT_PASSWORD=secretpass \
  mongo:7 \
  mongod --replSet rs0 --bind_ip_all --keyFile /etc/mongo-keyfile

mongosh -u admin -p secretpass --port 27020 --eval "rs.initiate({ _id: \"rs0\", members: [{ _id: 0, host: \"127.0.0.1:27017\" }] })"
mongosh -u admin -p secretpass --port 27020 --eval "rs.status()"

