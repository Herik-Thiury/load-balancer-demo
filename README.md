# Load Balancer Demo — Escalabilidade Horizontal

Atividade de Sistemas Distribuídos: uma aplicação web simples rodando em **três instâncias idênticas**, com um **balanceador de carga NGINX** distribuindo as requisições entre elas.

O objetivo não é a complexidade da aplicação, e sim a **camada de rede**: provar que o balanceador revez a de fato entre as instâncias, tolera a queda de uma delas e permite escalar.

---

## Tecnologias utilizadas

| Camada | Tecnologia |
|---|---|
| Aplicação | Java 21 + Spring Boot (Spring Web) |
| Balanceador de carga | NGINX (proxy reverso, round-robin) |
| Empacotamento | Docker |
| Orquestração | Docker Compose |

---

## Arquitetura

```
                    +------------------+
                    |     Cliente      |
                    |   (navegador)    |
                    +--------+---------+
                             |
                             | http://localhost:8080
                             v
                    +------------------+
                    |      NGINX       |   <- único ponto de entrada
                    |  (round-robin)   |
                    +--------+---------+
                             |
          +------------------+------------------+
          |                  |                  |
          v                  v                  v
    +-----------+      +-----------+      +-----------+
    |  app-01   |      |  app-02   |      |  app-03   |
    | (azul)    |      | (verde)   |      | (laranja) |
    |  :8080    |      |  :8080    |      |  :8080    |
    +-----------+      +-----------+      +-----------+
```

- O cliente acessa **apenas** `localhost:8080`, que cai no NGINX. As instâncias nunca são acessadas diretamente.
- Cada instância é o **mesmo** código; o que as diferencia é a variável de ambiente `INSTANCE_ID`, injetada pelo Docker Compose.
- Cada instância se identifica na resposta de três formas: nome e cor de fundo na página HTML, e o cabeçalho HTTP `X-Served-By`.

---

## Como o balanceamento foi configurado

O balanceamento é feito pelo NGINX, no arquivo [`nginx.conf`](./nginx.conf):

```nginx
upstream backend {
    server app-01:8080 max_fails=1 fail_timeout=5s;
    server app-02:8080 max_fails=1 fail_timeout=5s;
    server app-03:8080 max_fails=1 fail_timeout=5s;
}
```

- **Política:** round-robin (padrão do NGINX) — reveza as requisições em ordem entre as três instâncias.
- **Nomes de serviço:** `app-01`, `app-02`, `app-03` são resolvidos pela rede interna criada pelo Docker Compose (o NGINX encontra os apps pelo nome do serviço, não por IP).
- **Tolerância a falhas:** `max_fails=1 fail_timeout=5s` — se uma instância falhar uma vez, o NGINX a marca como indisponível por 5 segundos e para de enviar tráfego para ela.

Cada instância expõe também uma rota `/health` que responde `OK`, usada para verificar se o app está no ar.

---

## Como executar

**Pré-requisitos:** Docker Desktop instalado e em execução.

Na raiz do projeto, um único comando sobe as três instâncias e o balanceador:

```bash
docker compose up --build
```

Depois, acesse **http://localhost:8080** no navegador e recarregue a página (Ctrl+F5) várias vezes — o nome e a cor da instância que respondeu vão alternar.

Para parar:

```bash
docker compose down
```

---

## Como reproduzir os testes de verificação

> Os comandos `curl` abaixo usam `curl.exe` para o PowerShell (Windows). Em Linux/macOS, use apenas `curl`.

### 1. Teste visual de alternância
Acessar `http://localhost:8080` no navegador e recarregar com Ctrl+F5 dez vezes. O indicador (nome + cor) deve apontar instâncias diferentes em rodízio.

### 2. Verificação por linha de comando
```powershell
curl.exe -s http://localhost:8080 -D - -o nul | Select-String "X-Served-By"
```
Repetir algumas vezes e observar o valor de `X-Served-By` mudando (app-01, app-02, app-03).

### 3. Distribuição em volume
```powershell
1..30 | ForEach-Object {
    (curl.exe -s http://localhost:8080 -D - -o nul | Select-String "X-Served-By").ToString()
} | Group-Object | Select-Object Count, Name
```
Resultado esperado: aproximadamente 10 requisições para cada instância.

### 4. Tolerância a falhas
```powershell
docker compose stop app-02      # derruba uma instância
# recarregar o navegador: o serviço continua no ar, sem o app-02
docker compose start app-02     # reintegra a instância
```

### 5. Escala
Adicionar um serviço `app-04` no `docker-compose.yml` e a linha correspondente no bloco `upstream` do `nginx.conf`, depois subir novamente com `docker compose up --build`. A nova instância passa a receber requisições.

### 6. Evidência nos logs
```powershell
docker compose logs nginx
docker compose logs app-01
```

### Prova negativa
As instâncias não expõem porta para o host — apenas o NGINX. Portanto não é possível acessar um app diretamente, o que evidencia que a alternância observada vem do balanceador, e não da aplicação.

---

## Como a solução poderia evoluir

- **Mais instâncias:** basta adicionar novos serviços no `docker-compose.yml` e novas linhas no `upstream` do `nginx.conf`.
- **Auto scaling:** com um orquestrador como Docker Swarm ou Kubernetes, o número de instâncias poderia crescer e diminuir automaticamente conforme a carga, sem edição manual da configuração.
- **Sticky sessions:** se a aplicação guardasse estado em memória (ex.: sessão de login), a alternância entre instâncias faria esse estado "desaparecer". Isso se resolve com *sticky sessions* (o balanceador fixa cada usuário numa instância) ou, melhor, movendo o estado para um armazenamento externo compartilhado (Redis, banco de dados).
- **Outras políticas de balanceamento:** o NGINX suporta `least_conn` (envia para a instância com menos conexões ativas) e `ip_hash` (fixa cada IP numa instância), além do round-robin usado aqui.

---

## Autor

Hérik Thiury Silva de Lima — Análise e Desenvolvimento de Sistemas, IFPE Campus Jaboatão dos Guararapes.
