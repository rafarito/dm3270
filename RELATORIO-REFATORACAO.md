# Refatoração estrutural do dm3270 — relatório de progresso e continuação

**Branch:** `refactor/solid-architecture` (nos dois repositórios)
**Base:** `main`
**Estado:** Onda 0 e Onda 1 concluídas. Ondas 2 a 6 pendentes.
**Última verificação:** 1.246 testes verdes, `mvn clean test` → BUILD SUCCESS

Este documento existe para que outra pessoa — ou outro agente — possa continuar o trabalho
sem reconstruir o contexto. Ele registra o diagnóstico, as decisões tomadas com o usuário, o
que foi feito, **as armadilhas encontradas** e o que falta, com apontadores concretos.

Leia também, na ordem:

1. [RELATORIO-SOLID.md](RELATORIO-SOLID.md) — o diagnóstico original (não versionado; `*.md`
   é ignorado por padrão). É a fonte da numeração das ondas e das seções `§`.
2. [BACKLOG-DEFEITOS.md](BACKLOG-DEFEITOS.md) — os defeitos reais que **não** devem ser
   corrigidos nesta refatoração.
3. [TESTING.md](TESTING.md) — como rodar, o que cada mecanismo de segurança protege.

---

## 1. As três regras que governam este trabalho

Foram decididas com o usuário e **não são negociáveis sem falar com ele de novo**.

### Regra 1 — Preservar 100% do comportamento observável, zero correções

A refatoração muda organização e acoplamento. Não muda o resultado de nenhuma operação,
nenhuma mensagem de log, nenhum pixel. Os defeitos encontrados no caminho estão registrados
em [BACKLOG-DEFEITOS.md](BACKLOG-DEFEITOS.md) e foram **deliberadamente preservados**,
inclusive quando o código novo teve de reproduzir o defeito de propósito.

Consequência prática: **não existem commits `fix` nesta branch**, com uma exceção
documentada (ver §4.3). Se algo com cara de `fix` aparecer, vai para o backlog num commit
`docs`.

### Regra 2 — Artefato Maven único, camadas impostas por ArchUnit

Sem split multi-módulo no `dm3270`. O jar shaded do release não muda. A separação de camadas
é imposta por testes que quebram o CI — ver §3.2.

### Regra 3 — API de plugins segregada, mas compatível

`Plugin` continua existindo com os `default` atuais. JARs de terceiros já compilados têm de
continuar carregando.

### Regra 4 — A duplicação entre módulos de plugin é proposital

No `dm3270-plugins`, `Document.java`, `DocumentPage.java` e `ScreenBuilder.java` existem em
mais de um módulo. **O usuário quer que continuem duplicados.** Cada plugin é independente e
pode implementar a mesma ideia de formas diferentes — e já implementa: o `Document` do
`ShowDataset` monta linhas numeradas (`"000100 //JOB1 JOB"`, `col = leftColumn + 6`) e o do
`DownloadDataset` monta conteúdo puro (`"//JOB1 JOB"`, `col = leftColumn - 1`, com reset da
serpentina). Os dois comportamentos têm teste próprio.

**Isto corrige o §6.2 do diagnóstico**, que lia a divergência como cópia defasada e sugeria
extrair um módulo `dm3270-plugins-common`. Essa sugestão está descartada.

---

## 2. O diagnóstico, e o que dele foi verificado

O relatório original apontou três problemas que se reforçam. Conferi cada um contra o código.

### 2.1 A camada de protocolo dependia da GUI — **confirmado, era a causa raiz**

`Buffer.process (Screen)` obrigava 25 implementações espalhadas por `commands`, `orders`,
`telnet`, `extended`, `structuredfields` e `filetransfer` a conhecer uma classe de 1.010
linhas que estende `javafx.scene.canvas.Canvas`.

**Verificação empírica que vale registrar:** não era só inconveniente, era impeditivo. Tentei
montar um golden master do processamento instanciando uma `Screen` real e descobri que
`WriteCommand.process` chama `screen.getPluginsStage ().processPluginAuto ()`, e
`PluginsStage.autoRegisterDiscoveredPlugins` marca os plugins descobertos como **ativos**
(`entry.activate.setSelected (true)`). O resultado do teste dependeria do conteúdo do
diretório `plugins/` de cada máquina. **Não era possível executar uma única order 3270 sem
construir a aplicação inteira** — banco SQLite, três janelas, class loader de plugins.

### 2.2 Não existia camada — **confirmado, com correção de método**

O diagnóstico contou 25 ciclos mútuos entre pacotes. Minha medição encontrou **24**. A
diferença é de método: o relatório contou declarações de `import`; o ArchUnit lê as
dependências reais do bytecode, que incluem tipos de campo e de assinatura que não aparecem
como import e excluem import declarado e não usado. **A contagem do bytecode é a que vale**,
porque descreve o acoplamento que o compilador impõe.

### 2.3 As classes problemáticas eram as sem teste — **confirmado, e a causa não era escopo**

O `pom.xml` dizia que `targetClasses` estava "restrito aos pacotes que possuem teste". A
causa real era outra: `display`, `application`, `assistant` e `console` eram **intestáveis**.
O `TESTING.md` já registrava isso e apontava "extrair o modelo da view" como o refactor de
maior retorno do projeto. Foi o que a Onda 1 fez.

### 2.4 Um erro do diagnóstico

O §6.2 tratava as cópias de `Document`/`DocumentPage` nos plugins como cópia defasada a ser
unificada. São divergências intencionais — ver Regra 4.

---

## 3. A rede de segurança (Onda 0)

Tudo isto roda no `mvn test`. **É o que sustenta a promessa da Regra 1.** Quem continuar o
trabalho não deve tocar em `src/` sem que estes mecanismos estejam verdes.

### 3.1 Golden master do parser

`test/com/bytezone/dm3270/session/ParserGoldenMasterTest.java` +
`test/golden/mf-parse.txt` (1.688 linhas)

Reprocessa [`mf.txt`](src/com/bytezone/dm3270/application/mf.txt) — uma **sessão TN3270 real
gravada**, já versionada no classpath e usada em produção pelo `MainframeStage` — e congela a
estrutura de tudo que o parser monta: registros separados pelo `TelnetProcessor`, comandos e
orders montados por `Command.getCommand`, respostas telnet geradas.

Duas seções, por cobrirem caminhos diferentes:

- **sessão gravada** — dados 3270 reais, caminho básico (offset 0). Inclui o remonte de um
  registro que chega partido entre dois buffers TCP (o buffer 02 não produz saída sozinho).
- **negociação TN3270E** — sequência sintética que um host manda ao abrir a sessão. É o que
  `mf.txt` não alcança, porque o arquivo não tem negociação nenhuma. Cobre
  `processTelnetCommand`, `processTelnetSubcommand`, a geração das respostas e o caminho
  estendido de `processRecord`, com cabeçalho de 5 bytes.

Cobre de uma vez `telnet`, `buffers`, `commands`, `orders`, `extended`, `structuredfields` e
`replyfield`. **Enquanto o snapshot não mudar, o parser não mudou.**

Em caso de divergência grava `target/golden/mf-parse.actual.txt` e aponta a primeira linha
diferente. A comparação normaliza CRLF nos dois lados — sem isso o teste passaria só na
máquina que gerou o snapshot.

### 3.2 Regras de camada (ArchUnit)

`test/com/bytezone/dm3270/architecture/LayeringTest.java` + `test/archunit-baseline/`

Cinco regras **congeladas** com `FreezingArchRule`. O baseline é versionado: violação nova
quebra a build, violação existente não. Estado atual:

| Regra | Violações |
|---|---:|
| `protocolDoesNotDependOnDisplay` | 163 |
| `uiIsTheOnlyPlaceThatKnowsJavaFx` | 385 |
| `displayDoesNotDependOnDatabase` | 46 |
| `protocolDoesNotKnowJavaFx` | 31 |
| `displayDoesNotDependOnApplication` | 14 |

**Quando uma regra chegar a zero, troque `FreezingArchRule.freeze (regra)` pela regra nua.**
Dali em diante ela vale integralmente.

#### Como reabsorver violações redescritas — leia antes de usar

O `FreezingArchRule` identifica cada violação **pelo texto**, e o texto inclui a assinatura
completa do método. Trocar o tipo de um parâmetro reescreve a descrição da *mesma*
dependência, que então não casa mais com a entrada guardada e **aparece como nova**. Isso vai
acontecer em toda onda.

```bash
mvn test -Dtest=LayeringTest -Darchunit.freeze.refreeze=true
```

O refreeze é deliberado de propósito, não automático. **Confira a conta antes de aceitar:**
some as violações que sobreviveram à poda com as reportadas como novas e compare com o total
anterior. Se der mais que antes, houve aumento real de acoplamento escondido atrás do
refreeze. Foi assim que validei a Onda 1.3: 83 sobreviventes + 80 redescritas = 163, contra
166 antes — queda líquida de 3.

### 3.3 Placar de ciclos

`LayeringTest.MAX_MUTUAL_CYCLES`, hoje **23**.

É uma contagem, não um baseline congelado, por decisão: congelar `beFreeOfCycles` gerava um
baseline de 6.883 linhas, porque a regra lista cada dependência de cada ciclo. Um arquivo
desse tamanho não serve de placar — o diff não é legível.

**O teste falha nos dois sentidos.** Se subir, houve regressão. Se cair, o limite tem de ser
atualizado no mesmo commit que produziu o ganho — progresso que ninguém registra é progresso
que se perde na próxima regressão.

### 3.4 Testes de caracterização

Congelam o comportamento atual das classes que serão desmontadas, **incluindo os defeitos**.

| Teste | Congela |
|---|---|
| `SiteTest` (31 testes) | os getters de `Site` **com** o efeito colateral: `getPort()` reescreve o widget para `"23"`; `toString()` chama `getPort()`, então imprimir um Site com porta inválida **altera o Site** |
| `ScreenContextPoolingTest` (10) | a identidade de cor no pool — ver §5.1, a armadilha mais séria que encontrei |
| `PaletteFidelityTest` (59) | cada uma das 14 cores contra a constante JavaFX que substituiu |
| `ScreenPositionDrawingTest` (14) | a sequência exata de operações de desenho, com argumentos |
| `HeadlessProcessingTest` (11) | comandos 3270 executados de verdade, sem GUI |

### 3.5 Infraestrutura

- `test/com/bytezone/dm3270/testing/JavaFxToolkit.java` — extensão JUnit 5 que liga o
  toolkit uma vez por JVM. Use com `@ExtendWith (JavaFxToolkit.class)`. Só necessária para
  classes genuinamente visuais, como `Site`.
- `test/com/bytezone/dm3270/display/HeadlessScreenTarget.java` — a tela sem GUI. Ver §4.2.
- CI roda sob `xvfb-run --auto-servernum`, e a branch `refactor/**` dispara o workflow.

---

## 4. O que foi feito

13 commits. `git log --oneline main..HEAD`.

### 4.1 Onda 0 — rede de segurança (6 commits, zero linhas de `src/`)

```
17d360f7 test(session): congelar o parse de uma sessao TN3270 como golden master
bc7b9705 test(display): congelar a identidade de cor no pool de ScreenContext
ddd1f18a test(utilities): caracterizar o Site antes de separar dominio e formulario
2f925199 test(architecture): impor as camadas com ArchUnit e um placar de ciclos
81f344bf build(ci): rodar a suite sob xvfb e acompanhar a branch de refatoracao
7e6c5a4f docs: registrar a rede de seguranca e os defeitos que nao serao corrigidos
```

**Desvio do plano que precisa ser conhecido:** o plano previa um "golden master do
processamento" instanciando uma `Screen` real. Isso é inviável — ver §2.1. O snapshot
dependeria do conteúdo de `plugins/` de cada máquina. Foi substituído, na Onda 1, pelo
`HeadlessProcessingTest`, que cobre o mesmo terreno de forma determinística.

### 4.2 Onda 1 — cortar protocolo → GUI (6 commits)

```
00c5a1da refactor(attributes): trocar javafx Color por TerminalColor na paleta 3270
412d682d refactor(display): extrair ScreenCanvas e tirar GraphicsContext de ScreenPosition
5bc77bdc refactor(display): guardar a metrica da fonte em vez do medidor
b4577a63 refactor(buffers): trocar Screen por ScreenTarget em Buffer.process
37ea1cf9 test(display): executar comandos 3270 sem interface grafica
13761cdf build(pit): incluir o modelo de tela no mutation testing
```

Quatro amarras entre o modelo de tela e o JavaFX, cortadas em ordem:

| # | Amarra | Solução | Arquivos novos |
|---|---|---|---|
| 1 | A paleta era `javafx.scene.paint.Color` | `TerminalColor`, um `record` neutro. Conversão só ao desenhar | `attributes/TerminalColor.java`, `display/FxPalette.java` |
| 2 | `ScreenPosition` desenhava no `GraphicsContext` | `ScreenCanvas` com as 5 operações usadas; adaptador delega | `display/ScreenCanvas.java`, `display/FxScreenCanvas.java` |
| 3 | `ScreenContext` media a fonte via nó `Text` | `FontMetrics` guarda o resultado da medição, não o medidor | `display/FontMetrics.java` |
| 4 | `Buffer.process` recebia a classe `Screen` | `ScreenTarget` (estende a `DisplayScreen` que já existia); `CursorHost` faz o mesmo pelo `Cursor` | `display/ScreenTarget.java`, `display/CursorHost.java`, `display/ScreenOption.java` |

**Duas travessias foram estreitadas de propósito**, porque devolver o objeto inteiro só
disfarçaria o acoplamento:

- `getFieldManager ()` → `getFieldCount ()` e `getFieldAt (int)`. O protocolo usava o
  `FieldManager` só para contar campos e achar um campo por posição. `FieldManager` importa
  `database` e `plugins` e sobe uma thread SQLite no construtor.
- `getPluginsStage ()` → `processPluginAuto ()`. `WriteCommand` pedia a `Stage` do JavaFX
  apenas para chamar um método dela. **É este estreitamento que derruba o ciclo
  `commands ⇄ plugins`.**

#### Sobre o tamanho do `ScreenTarget`

24 membros. É gordo, e **não há ISP nenhum em fingi-lo pequeno**: quebrar em interfaces de
papel menores não reduziria acoplamento algum, porque Java não permite estreitar o tipo do
parâmetro ao sobrescrever um método — toda implementação de `process` continuaria vendo o
contrato inteiro. O ganho está em outro lugar: nenhuma classe de protocolo conhece mais
`Screen`, `FieldManager` ou `PluginsStage`. **Encolher o contrato depende de decompor
`Screen`, que é a Onda 3.**

#### Descoberta que economiza trabalho

`orders` **já estava inteiramente sobre `DisplayScreen`** — `Order.process (DisplayScreen)`.
A abstração correta já estava em uso lá. Os usos de `Screen` que apareciam no grep eram
linhas comentadas. Não foi preciso mexer em nenhuma das 13 orders.

#### O retorno: `HeadlessScreenTarget`

`test/com/bytezone/dm3270/display/HeadlessScreenTarget.java` implementa `ScreenTarget` e
`CursorHost`. **O buffer de tela dentro dele não é simulado**: são um `Pen` e um vetor de
`ScreenPosition` reais, ligados a um canvas que descarta o desenho.

O que ele ainda **não** faz, e está escrito no topo da classe:

- `getFieldCount ()` devolve 0 e `getFieldAt ()` devolve vazio, porque `FieldManager` exige a
  `Screen` concreta. Enquanto isso não cair (Onda 3), os ramos de `WriteCommand.process` que
  dependem de haver campos não são percorridos: `checkRecording` e `processPluginAuto` ficam
  de fora.
- `getTransferManager ()` devolve `null` (exige um `Site`).
- `getSystemMessage ()` é **real** — o construtor só guarda três valores, e
  `WriteCommand.process` termina sempre chamando `checkSystemMessage`; devolver `null` ali
  fazia todo comando estourar.

### 4.3 Um commit que não é meu

`cfc95f18 fix(streams): avisar o usuario quando a conexao com o servidor falha`

Foi feito por outro agente, a pedido do usuário, no meio do trabalho. É a **única mudança de
comportamento nesta branch**. Verifiquei que não afeta as redes de segurança: golden master e
baseline intactos, suíte verde.

Consequência para quem continuar: se alguém bisectar esta branch procurando quando o
comportamento mudou, **esse commit é o único candidato**. Todos os `refactor` são verificados
contra o golden master.

---

## 5. Armadilhas encontradas — leia antes de mexer em `src/`

### 5.1 `ScreenContext.matches` compara cor por identidade, não por valor

A mais séria. [ScreenContext.java](src/com/bytezone/dm3270/display/ScreenContext.java) usa
`==` nas cores, não `equals`. Isso só funciona porque toda cor vem do array de constantes de
`ColorAttribute` — e nesse array **três posições apontam para a mesma instância**: Neutral1
(0), Neutral2 (7) e White (15). São **16 slots para 14 objetos**.

O refactor óbvio seria um `enum` de 16 constantes. Isso mudaria o tamanho do pool de
`ContextManager` e as respostas de `matches` **sem quebrar nada visivelmente**. O array de
`TerminalColor` reproduz o aliasing referenciando `WHITE_SMOKE` três vezes, e
`ScreenContextPoolingTest` existe para provar que sobreviveu.

Relacionado: `TerminalColor.toString()` reproduz o formato do JavaFX (`0xrrggbbaa`) porque
`ColorAttribute.getName` cai nele para cores fora da paleta e `ScreenContext.toString`
imprime o resultado no log. Mudar o formato mudaria saída observável.

### 5.2 A compilação incremental do Maven esconde erros de assinatura

Ao trocar `Buffer.process (Screen)` por `process (ScreenTarget)`, o `mvn test-compile`
passou — mas o stub `TestBuffer` em `BufferTest` continuava com a assinatura antiga. O
compilador só recompila fontes alteradas e **não reavalia quem dependia delas**. Só apareceu
no `mvn clean test`.

**Verificação de mudança de assinatura é sempre com `mvn clean test`.**

### 5.3 O `pom.xml` não declara `<testResources>`

Só as classes compiladas de `test/` vão para o classpath. Por isso:

- o golden master lê o snapshot por caminho relativo (`test/golden/...`), a partir do
  `basedir`, que é o diretório de trabalho do Surefire;
- o caminho do baseline do ArchUnit é configurado **por código**, em `ArchUnitStore`, e não
  por um `archunit.properties`.

### 5.4 O `.gitignore` ignora `*.md`

Há exceções explícitas para `README.md`, `TESTING.md`, `BACKLOG-DEFEITOS.md` e este arquivo.
Qualquer documento novo precisa da sua. **`RELATORIO-SOLID.md` não está versionado.**

### 5.5 Identidade de autoria

O `user.email` local dos dois repositórios aponta para o e-mail corporativo, mas os commits
devem sair como `rafarito <rafaritogames@gmail.com>`. Já está configurado por repositório
nesta branch. **Nunca adicionar trailer de co-autoria ou atribuição a IA.**

### 5.6 O CI dos plugins não vai enxergar a Onda 5

`dm3270-plugins/.github/workflows/ci.yml` baixa o jar da **última release** de
`rafarito/dm3270`, não do build local. Quando a API de `Plugin` mudar, os plugins vão
compilar contra o jar antigo e o CI quebrará por um motivo falso. **Tem de passar a construir
o dm3270 do checkout antes de começar a Onda 5.**

Relacionado, e no backlog: `dm3270-plugins/.github/workflows/release.yml` compila cinco
plugins com `javac` direto e **nunca empacota o `UploadDataset`**.

---

## 6. Métricas

| | Início | Agora |
|---|---:|---:|
| Testes (`dm3270`) | 1.110 | **1.246** |
| Cobertura de instruções | 38,7% | **42,3%** |
| Mutantes gerados | 3.234 | **3.389** |
| Mutation coverage | 60% | **62%** |
| Ciclos mútuos | 24 | **23** |
| Protocolo conhece JavaFX | 58 | **31** |
| Só a UI conhece JavaFX | 412 | **385** |

Por pacote, o que subiu de forma visível: `attributes` 86%→91%, `commands` 48%→61%,
`utilities` 40%→60%, `orders` 85%→90%, `display` 0,5%→10%.

**`Cursor` está fora do `targetClasses` do PIT de propósito.** É instanciável headless desde
a extração de `CursorHost`, e o `HeadlessProcessingTest` o exercita de passagem, mas nenhum
teste verifica comportamento de cursor: 117 mutantes, zero mortos. Incluir uma classe assim
afunda a métrica sem informar nada. **Volta quando tiver teste próprio** — e escrever esse
teste é uma tarefa pequena e de bom retorno para quem continuar.

---

## 7. O que falta

### 7.1 Estado dos ciclos

23 pares mútuos. Os que sobraram, e o que cada um exige:

```
application ⇄ assistant        application ⇄ display     application ⇄ plugins
application ⇄ session          application ⇄ streams     assistant   ⇄ display
assistant   ⇄ filetransfer     attributes  ⇄ display     attributes  ⇄ orders
buffers     ⇄ streams          commands    ⇄ display     commands    ⇄ filetransfer
commands    ⇄ streams          commands    ⇄ structuredfields
console     ⇄ display          display     ⇄ filetransfer
display     ⇄ orders           display     ⇄ plugins     display     ⇄ streams
session     ⇄ streams          streams     ⇄ telnet
reporter.file ⇄ reporter.reports          reporter.record ⇄ reporter.text
```

**Zero não é a meta realista.** `commands ⇄ display` é inerente ao modelo 3270: um
`ReadCommand` pede à tela que produza um `AIDCommand`, e `AIDCommand` é um comando de
protocolo. A meta é eliminar os acidentais — `display ⇄ plugins`, `display ⇄ database`,
`display ⇄ application` — e deixar documentados os poucos inerentes.

`attributes ⇄ display` persiste porque `StartFieldAttribute.process` recebe `ContextManager`
e `ScreenContext`. Cai quando o modelo de tela sair de `display` para um pacote próprio.

### 7.2 Alvo imediato de melhor retorno

**As 31 violações de JavaFX restantes no protocolo vêm de apenas duas classes:**
`commands.Profile` e `commands.SystemMessage`. Resolver essas duas leva a regra
`protocolDoesNotKnowJavaFx` a **zero**, e aí ela pode ser descongelada — o protocolo passa a
ter garantia permanente de rodar headless. É a tarefa de melhor relação esforço/retorno
disponível agora.

`SystemMessage` usa `Font` num único caminho (mensagem de IPL do console). `Profile` monta
labels. Os dois pedem extração da parte visual para a camada de UI.

### 7.3 Onda 2 — extrair o domínio de dentro das classes de UI

| Passo | Arquivos | Notas |
|---|---|---|
| `record Site` + `SiteValidator` + `SiteForm` | [utilities/Site.java](src/com/bytezone/dm3270/utilities/Site.java) (163 linhas), `SiteListStage`, `Console`, `OptionStage`, `TransferManager`, `TelnetSocket` | **O `SiteForm` tem de manter a correção silenciosa** do `getPort`/`getModel`, inclusive o `setText("23")`. `SiteTest` já congela isso, com um teste dedicado ao efeito da segunda leitura |
| `ReportScore` vira valor imutável | [reporter/file/ReportScore.java](src/com/bytezone/reporter/file/ReportScore.java) | `Pagination`, `TextArea` e o bloco `static` que resolve fontes migram para um `ReportScoreView` |
| `interface NamedBuffer extends Buffer` | [session/SessionRecord.java:74-83](src/com/bytezone/dm3270/session/SessionRecord.java#L74-L83) | Os 5 tipos **já têm** `getName()`; só falta a interface comum. O próprio comentário no código aponta a solução |
| `OptionStage` devolve um `LaunchRequest` | `application/OptionStage.java`, [Console.java:315-338](src/com/bytezone/dm3270/application/Console.java#L315-L338) | Hoje o `Console` lê 8 campos package-private de widgets direto |

Unificar `TableDataset`/`Dataset`/`Member` (§8.1 do diagnóstico) fica **para o fim da Onda
3**, depois de `ScreenWatcher` e `DatabaseThread` decompostos — hoje são 7 pontos de edição
acoplados.

### 7.4 Onda 3 — quebrar as God Classes

Tamanhos atuais medidos:

| Arquivo | Linhas |
|---|---:|
| `display/Screen.java` | 1.032 |
| `display/ScreenWatcher.java` | 1.002 |
| `database/DatabaseThread.java` | 826 |
| `plugins/PluginsStage.java` | 760 |
| `display/FieldManager.java` | 478 |

**`FieldManager` primeiro.** É o de menor esforço e maior desbloqueio:
[FieldManager.java:53-68](src/com/bytezone/dm3270/display/FieldManager.java#L53-L68) sobe uma
`DatabaseThread` no construtor. Injetar um `DatasetStore` já pronto corta a aresta
`display → database` (46 violações) **e** faz o `HeadlessScreenTarget` passar a ter campos de
verdade, o que destrava os ramos de `WriteCommand.process` hoje não percorridos.

**`Screen`** decompõe em: `ScreenBuffer`, `KeyboardState`, `ScreenRenderer`,
`SelectionController`, `AidCommandBuilder`, `ApplicationContext`. O construtor atual tem 54
linhas e vaza `this` para 10 colaboradores parcialmente construídos, incluindo
`pluginsStage.setScreen (this)`, que fecha um ciclo de objetos. `BuildInstruction`
(linha ~103) é enum morto — remover na Onda 6.

**`ScreenWatcher`**: Strategy por layout, com os offsets de coluna atuais **inalterados**.
Hoje `checkDatasetList` (135 linhas) e `addDataset` (88 linhas) repetem a numeração de
`screenType` em dois lugares. **Não há teste de caracterização para isto ainda** — escrever
antes de mexer. Depende de `FieldManager`, que depende da `Screen` concreta; ou seja, vem
depois do passo do `FieldManager`.

**`DatabaseThread`**: `implements Runnable` num executor de thread única, `DatasetRepository`
e `MemberRepository` com `Connection` injetada, `SchemaInitializer` para o DDL, e
`request.execute (repos)` polimórfico substituindo a cadeia de `instanceof` em
[run():94-98](src/com/bytezone/dm3270/database/DatabaseThread.java#L94-L98). `DatabaseTest`
já tem 23 testes; ampliar antes de decompor.

**`PluginsStage`**: `PluginRegistry` + `PluginClassLoaderFactory` + `PluginExecutor` +
a janela. **As 5 heurísticas de reescrita de nome de pacote em
[getClassCandidates](src/com/bytezone/dm3270/plugins/PluginsStage.java#L552-L578) ficam
intactas** — são camada de compatibilidade com plugins já instalados.

### 7.5 Onda 4 — despacho polimórfico

| Alvo | Mudança |
|---|---|
`application/ConsoleKeyPress.java` (246 linhas, `handle` com 212) | **Dois passos.** Primeiro *Extract Method* puro, verificado pelo compilador; escrever um teste por binding **entre** os dois passos; só então `Map<KeyCombination, TerminalAction>` |
| `Console.startSelectedFunction` | `LaunchMode` polimórfico. O enum `Console.Function` já existe e não é usado no despacho |
| `Console.setModel` | `TerminalModel` com as dimensões. **O `case 5` continua caindo no `default` e logando `"Invalid model number: 5"`** — Regra 1 |
| `AIDCommand` (7 `instanceof`), `Session` (6) | Despacho via `NamedBuffer`, guardado pelo golden master |

### 7.6 Onda 5 — API de plugins

1. **Antes de tudo:** corrigir o CI dos plugins para construir o dm3270 do checkout (§5.6).
2. `AutoPlugin` / `RequestPlugin` / `Activatable`, com `Plugin` mantendo os `default`
   atuais e o host despachando por `instanceof` **com fallback** para `doesAuto ()`.
   Cuidado: `doesAuto` **é atribuído em 46 pontos** e muda durante a execução — em
   `FanLogon.processRequest` o plugin se transforma em automático no meio do fluxo. A máquina
   de estados tem de ser reproduzida, não simplificada.

   Contagem medida (`grep -c "doesAuto = "`), que difere dos 33 do diagnóstico porque inclui
   o `DatasetStage`, que ele não contou:

   | Arquivo | Atribuições |
   |---|---:|
   | `DownloadDataset/DownloadDataset.java` | 11 |
   | `FanLogon/FanLogon.java` | 11 |
   | `ShowDataset/ShowDataset.java` | 11 |
   | `UploadDataset/UploadDataset.java` | 6 |
   | `ShowDataset/DatasetStage.java` | 4 |
   | `FanLogoff/FanLogoff.java` | 3 |
3. `DefaultPlugin` deixa de ser herança. Os 7 métodos não são sobrescritos por ninguém — são
   utilitários estáticos entregues por herança, gastando o único slot de superclasse. Movem
   para `PluginData`. A classe permanece *deprecated* delegando.
4. **Um `URLClassLoader` por JAR.** Hoje
   [buildPluginClassLoader](src/com/bytezone/dm3270/plugins/PluginsStage.java#L588-L636)
   monta um único loader com todos os JARs, e os dois `com.bytezone.plugins.DocumentPage` com
   bytecode diferente colidem — a primeira definição encontrada vence para ambos, e qual
   depende da ordem de listagem do diretório. **Isto não é comportamento a preservar, é
   indeterminismo.** A correção faz cada plugin rodar deterministicamente o próprio código, e
   é o que **sustenta a Regra 4**.
5. `UploadDataset` (981 linhas) decompõe seguindo o padrão que o próprio repositório já
   acertou em `UploadContext`: domínio puro, sem JavaFX, com `prepare()` devolvendo erros.

### 7.7 Onda 6 — limpeza (só o provadamente morto)

- Remover [application/Terminal.java](src/com/bytezone/dm3270/application/Terminal.java) —
  160 linhas, 154 comentadas, `abstract` sem subclasse. **Confirmei: a única menção no resto
  do código é um comentário em `ConsolePane.java:319`.**
- Remover os 7 blocos `if (false)`: `Console.init`, `Console.start`, `PluginsStage:335`,
  `PenType1:67`, `TreePanel:70`, `ReportData:161`, `EraseUnprotectedToAddressOrder:32`.
- Desembrulhar os `if (true)` (`ReplayStage:75` e `:118`, `PenType1:193`,
  `ScreenWatcher:219`) **só onde não houver `else`** — verificar caso a caso.
- Remover as 371 linhas de código comentado.
- Remover o enum morto `Screen.BuildInstruction`.

**Não entram:** os 103 `assert` (o Surefire roda com `-ea`, então eles estão **ativos nos
testes**; convertê-los muda comportamento nos dois ambientes) e as correções do
[BACKLOG-DEFEITOS.md](BACKLOG-DEFEITOS.md).

---

## 8. Como verificar e commitar

```bash
# dm3270 — sempre com clean ao mexer em assinatura (§5.2)
mvn clean test

# ao fim de cada onda
mvn clean test-compile pitest:mutationCoverage

# dm3270-plugins
cd ../dm3270 && mvn install -DskipTests
cd ../dm3270-plugins && mvn test
```

Critérios para um commit ser elegível:

1. **Golden master do parser byte a byte idêntico.** É a prova central.
2. ArchUnit sem violações novas; conta do refreeze conferida (§3.2).
3. `MAX_MUTUAL_CYCLES` atualizado se caiu.
4. **Nenhum teste existente alterado para passar.** Se um teste quebra, ou a refatoração
   mudou comportamento (reverter) ou o teste dependia de detalhe interno — aí sim ajustar, e
   dizer isso na mensagem do commit.
5. JaCoCo e PIT não regridem.
6. Antes de fechar uma onda: `mvn javafx:run`, conectar num host, abrir o assistant, rodar um
   plugin auto e um request, fazer um download e um upload. **O golden master não cobre
   desenho nem interação de mouse e teclado.**

Commits em Conventional Commits, atômicos, com corpo explicando o quê e o porquê. Tipos:
`test` para rede de segurança, `refactor` para o grosso, `chore` para código morto, `build`
para pom/CI, `docs` para documentação. **Sem `fix`** (Regra 1).

---

## 9. Fora de escopo — não fazer

- Corrigir os defeitos do [BACKLOG-DEFEITOS.md](BACKLOG-DEFEITOS.md).
- **Deduplicar código entre módulos de plugin** (Regra 4).
- Split multi-módulo Maven do `dm3270` (Regra 2) — reavaliar só depois que o grafo estiver
  limpo.
- Migrar a descoberta de plugins para `ServiceLoader` — quebraria JARs já instalados.
- Trocar `Plugin` por uma API incompatível (Regra 3).
- Converter os 103 `assert`.
