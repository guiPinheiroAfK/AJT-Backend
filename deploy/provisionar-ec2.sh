#!/usr/bin/env bash
# ============================================================
#  Provisionamento da AJT numa EC2 (Ubuntu 22.04 ou 24.04)
#
#  Sobe tudo na mesma maquina, sem Docker:
#    - PostgreSQL 16/14 (do apt), escutando so em localhost
#    - back-end Spring Boot como servico do systemd (ajt-backend), em 127.0.0.1:8080
#    - build de producao do Angular servido pelo nginx na porta 80,
#      com fallback de rotas SPA e /api repassado pro back-end
#
#  Uso (na EC2, como root):
#    curl -fsSL https://raw.githubusercontent.com/guiPinheiroAfK/AJT-Backend/main/deploy/provisionar-ec2.sh -o provisionar-ec2.sh
#    sudo bash provisionar-ec2.sh
#
#  Pode rodar de novo quantas vezes quiser: atualiza o codigo, refaz os builds e
#  reinicia, sem trocar as senhas ja geradas (trocar a chave de criptografia
#  deixaria os documentos dos passageiros ilegiveis).
#
#  Variaveis opcionais (ex.: sudo DOMINIO=ajt.exemplo.com bash provisionar-ec2.sh):
#    DOMINIO     dominio ou IP publico (padrao: IP publico lido da propria EC2)
#    BACK_REPO   repositorio do back-end   (padrao: GitHub guiPinheiroAfK/AJT-Backend)
#    FRONT_REPO  repositorio do front-end  (padrao: GitHub k9milly/AJT-Frontend)
#    BRANCH      branch dos dois repositorios (padrao: main)
#    USAR_UFW    1 liga o firewall do Ubuntu (SSH + HTTP/HTTPS); 0 deixa so o Security Group (padrao: 1)
# ============================================================
set -euo pipefail

BACK_REPO="${BACK_REPO:-https://github.com/guiPinheiroAfK/AJT-Backend.git}"
FRONT_REPO="${FRONT_REPO:-https://github.com/k9milly/AJT-Frontend.git}"
BRANCH="${BRANCH:-main}"
USAR_UFW="${USAR_UFW:-1}"

APP_DIR=/opt/ajt
SRC_DIR="$APP_DIR/src"
JAR="$APP_DIR/ajt-backend.jar"
WEB_DIR=/var/www/ajt
ENV_DIR=/etc/ajt
ENV_FILE="$ENV_DIR/ajt.env"
SERVICO=ajt-backend
USUARIO=ajt
DB_NOME=ajt_db
DB_USUARIO=ajt

passo() { echo; echo "==> $*"; }
falha() { echo "ERRO: $*" >&2; exit 1; }

# ---------- 0. conferencias ----------
[ "$(id -u)" -eq 0 ] || falha "rode como root: sudo bash $0"
# shellcheck source=/dev/null
[ -r /etc/os-release ] && . /etc/os-release
[ "${ID:-}" = "ubuntu" ] || falha "este script foi feito para Ubuntu (encontrado: ${ID:-desconhecido})"
export DEBIAN_FRONTEND=noninteractive

# IP publico pelo servico de metadados da EC2 (IMDSv2); fora da AWS cai no IP local
descobrir_host() {
    local token ip
    token=$(curl -sf -m 2 -X PUT http://169.254.169.254/latest/api/token \
        -H "X-aws-ec2-metadata-token-ttl-seconds: 60" || true)
    if [ -n "$token" ]; then
        ip=$(curl -sf -m 2 -H "X-aws-ec2-metadata-token: $token" \
            http://169.254.169.254/latest/meta-data/public-ipv4 || true)
    fi
    echo "${ip:-$(hostname -I | awk '{print $1}')}"
}

# ---------- 1. memoria ----------
# t2/t3.micro tem 1 GB: o build do Maven e do Angular estoura sem swap
if [ "$(swapon --show --noheadings | wc -l)" -eq 0 ] && [ "$(awk '/MemTotal/ {print $2}' /proc/meminfo)" -lt 3000000 ]; then
    passo "Criando swap de 2 GB (maquina com pouca memoria)"
    fallocate -l 2G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# ---------- 2. pacotes ----------
passo "Instalando Java 17, Maven, PostgreSQL, nginx e git"
apt-get update -y
apt-get install -y openjdk-17-jdk-headless maven postgresql nginx git curl openssl ca-certificates

# o package-lock do front foi gerado pelo npm 11: precisa do Node 24 (o do apt e antigo)
versao_node=$(node -v 2>/dev/null | sed 's/^v\([0-9]*\).*/\1/' || true)
if [ "${versao_node:-0}" -lt 24 ]; then
    passo "Instalando Node.js 24 (NodeSource)"
    curl -fsSL https://deb.nodesource.com/setup_24.x | bash -
    apt-get install -y nodejs
fi

# ---------- 3. usuario do servico ----------
id "$USUARIO" >/dev/null 2>&1 || useradd --system --home-dir "$APP_DIR" --shell /usr/sbin/nologin "$USUARIO"
mkdir -p "$APP_DIR" "$SRC_DIR" "$WEB_DIR" "$ENV_DIR"

# ---------- 4. segredos ----------
# gerados uma vez so e guardados fora do git, legiveis apenas pelo root
if [ ! -f "$ENV_FILE" ]; then
    passo "Gerando senhas e chaves em $ENV_FILE"
    HOST_PUBLICO="${DOMINIO:-$(descobrir_host)}"
    umask 077
    cat > "$ENV_FILE" <<EOF
AJT_PROFILE=prod
SERVER_ADDRESS=127.0.0.1
AJT_DB_URL=jdbc:postgresql://localhost:5432/$DB_NOME
AJT_DB_USER=$DB_USUARIO
AJT_DB_PASSWORD=$(openssl rand -hex 24)
AJT_JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
AJT_JWT_EXPIRATION=86400000
AJT_CRYPTO_KEY=$(openssl rand -base64 32)
AJT_CORS_ORIGINS=http://$HOST_PUBLICO
EOF
    umask 022
fi
chmod 600 "$ENV_FILE"
DB_SENHA=$(grep '^AJT_DB_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)
HOST_PUBLICO=$(grep '^AJT_CORS_ORIGINS=' "$ENV_FILE" | cut -d= -f2- | sed 's#^https\?://##; s#,.*##')

# ---------- 5. banco ----------
passo "Configurando o PostgreSQL"
systemctl enable --now postgresql
# escuta so na propria maquina: a porta 5432 nunca fica aberta pra fora
for conf_d in /etc/postgresql/*/main/conf.d; do
    echo "listen_addresses = 'localhost'" > "$conf_d/ajt.conf"
done
systemctl restart postgresql

if sudo -u postgres psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='$DB_USUARIO'" | grep -q 1; then
    sudo -u postgres psql -qc "ALTER ROLE $DB_USUARIO WITH LOGIN PASSWORD '$DB_SENHA'"
else
    sudo -u postgres psql -qc "CREATE ROLE $DB_USUARIO WITH LOGIN PASSWORD '$DB_SENHA'"
fi
sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname='$DB_NOME'" | grep -q 1 \
    || sudo -u postgres createdb -O "$DB_USUARIO" "$DB_NOME"

# ---------- 6. codigo ----------
baixar() { # baixar <repositorio> <pasta>
    if [ -d "$2/.git" ]; then
        git -C "$2" fetch --depth 1 origin "$BRANCH"
        git -C "$2" checkout -q -B "$BRANCH" FETCH_HEAD
    else
        git clone --depth 1 --branch "$BRANCH" "$1" "$2"
    fi
}
passo "Baixando o codigo (branch $BRANCH)"
baixar "$BACK_REPO" "$SRC_DIR/backend"
baixar "$FRONT_REPO" "$SRC_DIR/frontend"

# ---------- 7. back-end ----------
passo "Compilando o back-end (pode levar alguns minutos)"
( cd "$SRC_DIR/backend/api" && mvn -q -B -DskipTests clean package )
cp "$SRC_DIR"/backend/api/target/ajt-backend-*.jar "$JAR"
chown "$USUARIO:$USUARIO" "$JAR"

cat > /etc/systemd/system/$SERVICO.service <<EOF
[Unit]
Description=AJT back-end (Spring Boot)
After=network.target postgresql.service
Requires=postgresql.service

[Service]
User=$USUARIO
EnvironmentFile=$ENV_FILE
WorkingDirectory=$APP_DIR
ExecStart=/usr/bin/java -XX:MaxRAMPercentage=50 -jar $JAR
SuccessExitStatus=143
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable $SERVICO
systemctl restart $SERVICO

# ---------- 8. front-end ----------
passo "Gerando o build de producao do Angular"
(
    cd "$SRC_DIR/frontend"
    npm ci --no-audit --no-fund --fetch-retries=5
    # build de producao usa o environment.prod.ts: apiUrl = /api (mesmo endereco do site)
    npm run build -- --configuration production
)
rm -rf "${WEB_DIR:?}"/*
cp -r "$SRC_DIR/frontend/dist/ajt-front/browser/." "$WEB_DIR/"

# ---------- 9. nginx ----------
passo "Configurando o nginx"
cat > /etc/nginx/sites-available/ajt <<'EOF'
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;

    root /var/www/ajt;
    index index.html;
    server_tokens off;

    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;

    # API: o back-end so escuta em 127.0.0.1, o acesso de fora passa por aqui
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $http_host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # js/css com hash no nome mudam a cada build: podem ficar em cache
    location ~* \.(?:js|css|woff2?|png|jpg|jpeg|gif|svg|ico)$ {
        expires 30d;
        add_header Cache-Control "public, immutable";
        try_files $uri =404;
    }

    # rotas do Angular (/login, /admin/transfers...) nao existem como arquivo: devolve o index.html
    location / {
        add_header Cache-Control "no-cache";
        try_files $uri $uri/ /index.html;
    }
}
EOF
ln -sf /etc/nginx/sites-available/ajt /etc/nginx/sites-enabled/ajt
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl enable nginx
systemctl reload nginx || systemctl restart nginx

# ---------- 10. firewall ----------
if [ "$USAR_UFW" = "1" ] && command -v ufw >/dev/null; then
    passo "Ligando o firewall (SSH, HTTP e HTTPS)"
    ufw allow OpenSSH
    ufw allow 'Nginx Full'
    ufw --force enable
fi

# ---------- 11. conferencia ----------
passo "Esperando o back-end subir"
for _ in $(seq 1 60); do
    status=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/auth/me || true)
    [ "$status" = "401" ] && break
    sleep 3
done
if [ "$status" != "401" ]; then
    journalctl -u $SERVICO -n 40 --no-pager
    falha "o back-end nao respondeu. Veja o log acima ou: journalctl -u $SERVICO -f"
fi
[ "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1/login)" = "200" ] \
    || falha "o nginx nao entregou o front. Veja: journalctl -u nginx"

cat <<EOF

============================================================
 AJT no ar: http://$HOST_PUBLICO

 Primeiro acesso: usuario admin / senha admin123
 (o sistema obriga a trocar a senha no primeiro login)

 Back-end:  systemctl status $SERVICO   |   journalctl -u $SERVICO -f
 Segredos:  $ENV_FILE (so o root le; nao copie pro git)

 Security Group da EC2: liberar so a 22 (SSH, de preferencia
 so pro seu IP) e a 80 (HTTP). Nao abrir a 5432 nem a 8080.
============================================================
EOF
