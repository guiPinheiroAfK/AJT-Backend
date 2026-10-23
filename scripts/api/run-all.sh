#!/bin/bash
cd "$(dirname "$0")"
chmod +x *.sh
./test-motoristas.sh
./test-passageiros.sh
./test-veiculos.sh
./test-usuarios.sh
./test-auth.sh
./test-transfers.sh
./test-pontos-coleta.sh
./test-ordens-servico.sh
./test-paradas-os.sh