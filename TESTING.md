# Testes

## Como rodar

```bash
# dm3270
mvn test

# dm3270-plugins (precisa do artefato do dm3270 no repositorio local)
cd ../dm3270 && mvn install -DskipTests
cd ../dm3270-plugins && mvn test
```

As classes de protocolo validam os buffers com `assert`, por isso o Surefire roda com
`-ea` habilitado nos dois projetos. Rodar os testes com assertions desligadas esconde
parte das verificacoes.

Alguns testes redirecionam `user.home` para um diretorio temporario, porque o codigo de
producao le e grava sob `~/dm3270` (`DatabaseThread`, `Parameters`, `FileSaver`). Eles
restauram a propriedade no fim, mas por isso a suite nao deve ser configurada para rodar
em paralelo sem antes isolar esses casos.

---

## O que neste arquivo esta atualizado, e o que nao esta

Este documento acompanha a refatoracao estrutural da branch `refactor/solid-architecture`, e
ficou defasado entre a Onda 1 e o Passo 5.

**Remedido no Passo 10, e portanto confiavel:** "Situacao atual", "Cobertura por pacote —
`dm3270`", "Mapa de modulos", "Uma falha intermitente que nao e sua", "Testes que exigem
JavaFX", "Rede de seguranca" e "Proximos alvos". Todos os numeros dessas secoes foram medidos
de novo com `mvn clean test` e `mvn test-compile pitest:mutationCoverage`.

**Ainda sao instantaneos antigos** — ordens de grandeza valem, numeros exatos nao: "Cobertura
por modulo — `dm3270-plugins`", a secao "Cobertura atual" (que duplica a de cima com dados mais
velhos) e a lista de "Defeitos corrigidos". Regenere antes de citar.

O estado corrente da refatoracao - o que falta, o que foi decidido e por que - esta no
`CLAUDE.md` e no `RELATORIO-REFATORACAO.md`, que **nao sao versionados** e por isso nao chegam
a quem clona o repositorio. Se voce e um agente e nao os tem, peca ao usuario.

## Metricas da suite

### Cobertura — JaCoCo

Sai junto com `mvn test`, sem comando extra:

```
dm3270           target/site/jacoco/index.html
dm3270-plugins   <modulo>/target/site/jacoco/index.html
```

Abra o `index.html` no navegador e navegue ate a classe: as linhas ficam verdes
(cobertas), amarelas (branch parcialmente coberto) ou vermelhas (nao executadas). Os
mesmos dados estao em `jacoco.csv` e `jacoco.xml`, no mesmo diretorio, para scripting.

### Mutation testing — PIT

Nao roda no ciclo padrao, porque leva minutos:

```bash
mvn test-compile pitest:mutationCoverage
```

```
dm3270           target/pit-reports/index.html
dm3270-plugins   <modulo>/target/pit-reports/index.html
```

O PIT altera o bytecode (troca `>` por `>=`, inverte condicoes, remove chamadas) e
verifica se algum teste falha. Um mutante que **sobrevive** e uma linha que os testes
executam mas nao verificam de verdade — e o tipo de buraco que a cobertura sozinha nao
mostra.

`targetClasses` esta restrito aos pacotes que possuem teste. Apontar o PIT para a camada
JavaFX geraria milhares de mutantes sem cobertura e afogaria o sinal util.
`TerminalServerTest` e `TelnetSocketTest` estao em `excludedTestClasses`: eles abrem
sockets de verdade e cada mutante ficaria pendurado no timeout.

### Como ler os dois numeros do PIT

| Metrica | O que significa |
|---|---|
| **Mutation coverage** | mutantes mortos / **todos** os mutantes. Cai junto com o codigo que nao tem teste nenhum. |
| **Test strength** | mutantes mortos / mutantes **cobertos**. Ignora o codigo sem teste e mede so a qualidade dos testes que existem. |

Num projeto com muita UI sem teste, o primeiro numero fica baixo por construcao; o segundo
e o que diz se os testes escritos valem alguma coisa.

### Situacao atual

Medido ao fim do Passo 10.

| | `dm3270` | `dm3270-plugins` |
|---|---:|---:|
| Testes | 1.452 | 248 (63 no `UploadDataset`) |
| Cobertura de instrucoes (projeto todo) | 51% | — |
| Cobertura de ramos | 48% | — |
| Mutantes gerados | 4.019 | — |
| Mutation coverage | 66% (2.642/4.019) | — |
| **Test strength** | **86%** (2.642/3.087) | — |
| Classes no `targetClasses` | 162 | — |

**Os numeros sao praticamente os mesmos do Passo 6, e isso e o esperado nos dois passos.** O
Passo 6 so cortou acoplamento; o Passo 10 so removeu codigo morto. Nenhum dos dois acrescentou
teste de comportamento. **A unica variacao e um mutante a menos** — 4.020 para 4.019 —, e ele
tem nome: era a chamada `void` de `ScreenWatcher.checkMenu`, que o mutator `VOID_METHOD_CALLS`
gerava e que **sobrevivia por ser equivalente**, porque o metodo era um `if (true) return;`.
Removido o metodo, o pacote `watch` subiu de 70% para 71% de mutacao sem que nenhum teste novo
tenha sido escrito. Um passo que so corta acoplamento ou so remove morto move um placar e nao
move o outro - e reconhecer isso e o que impede de procurar um ganho que nao existe.

Os 51% do projeto todo refletem a camada JavaFX sem teste, nao a qualidade da suite:
`application`, `assistant`, `console` e `reporter.application` somam mais de 20 mil
instrucoes e quase nenhum teste. Nos pacotes de protocolo a cobertura passa de 80%. Os
numeros do PIT nos plugins nao foram remedidos no Passo 5.

**O modelo de tela saiu desse grupo na onda de desacoplamento**, e depois dele mais quatro
pacotes: `datasets` na Onda 3, `watch` no Passo 1, `reporter.file` no Passo 4, e `session` e
`streams` no Passo 5. Todos rodam headless e todos estao no `targetClasses`, com uma excecao
explicada abaixo.

**O Passo 6 nao acrescentou pacote headless - todos os que ele tocou ja eram -, mas apertou
tres fronteiras:** `streams` nao nomeia mais `application`, e `buffers` e `commands` nao
nomeiam mais `streams`. As duas regras de camada novas,
`streamsDoesNotDependOnApplication` e `buffersAndCommandsDoNotKnowStreams`, nasceram em zero e
nao sao congeladas.

**Correcao de uma afirmacao antiga deste arquivo:** ele dizia que `Cursor` ficava de fora do
`targetClasses` de proposito, por ter 117 mutantes com zero mortos. **`Cursor` entrou na Fase
2**, junto com o `CursorTest`, e hoje mata 76 dos 117.

## Rede de seguranca da refatoracao estrutural

A refatoracao em curso preserva 100% do comportamento observavel. Quatro mecanismos
sustentam essa promessa, e todos rodam no `mvn test`:

| Mecanismo | Onde | O que protege |
|---|---|---|
| Golden master do parser | `ParserGoldenMasterTest` + `test/golden/mf-parse.txt` | Reprocessa uma sessao TN3270 real e congela tudo que o parser monta: registros, comandos, orders, respostas telnet. Cobre `telnet`, `buffers`, `commands`, `orders`, `extended`, `structuredfields` e `replyfield` de uma vez |
| Regras de camada | `LayeringTest` + `test/archunit-baseline/` | **Treze** regras de dependencia com ArchUnit. **Doze chegaram a zero e NAO sao congeladas** - uma violacao nova quebra a build sem baseline para absorve-la. So `uiIsTheOnlyPlaceThatKnowsJavaFx` segue congelada, em 240 violacoes, e o baseline versionado e o placar: ele so encolhe |
| Placar de ciclos | `LayeringTest.MAX_MUTUAL_CYCLES`, hoje **9** | Conta os pares de pacotes com dependencia mutua. Falha se subir **e** se cair sem atualizar o limite, para que todo ganho seja registrado no commit que o produziu |
| Caracterizacao | `SiteFormTest`, `ScreenContextPoolingTest`, `ReportScoreTest`, `SessionRecordTest`, `SessionTest`, e outros | Congela o comportamento atual das classes que serao desmontadas, **incluindo os defeitos** — ver [BACKLOG-DEFEITOS.md](BACKLOG-DEFEITOS.md) |

**Uma regra que chegou a zero e trocada pela regra nua**, com o baseline e a entrada dele no
`stored.rules` apagados. E isso que separa "hoje nao ha violacao" de "nao pode haver
violacao": um baseline pode ser refrozen com `-Darchunit.freeze.refreeze=true`, uma regra nua
nao pode ser afrouxada por comando nenhum.

Quando um golden master falhar, o teste grava o resultado obtido em `target/golden/` e
aponta a primeira linha divergente. Duas leituras possiveis: ou a refatoracao mudou
comportamento e deve ser revertida, ou a mudanca era pretendida e o snapshot precisa ser
reaprovado — o que exige justificativa no commit, nunca um `rm` silencioso.

### Testes que exigem JavaFX

`SiteForm` e `Screen` so podem ser instanciadas com o toolkit ativo. A extensao
`JavaFxToolkit` liga o toolkit uma vez por JVM; use com `@ExtendWith (JavaFxToolkit.class)`.

**A lista encolheu, e o encolhimento e o resultado da refatoracao.** `ScreenPosition` e `Pen`
sairam na Onda 1; `Site` virou uma porta com implementacao headless (`SiteValue`) no Passo 3;
`ReportScore` saiu no Passo 4; `Session` e `SessionRecord` sairam no Passo 5. Nos tres ultimos
casos **a ausencia da anotacao no teste e a assercao principal**, e esta dita no cabecalho de
cada classe de teste.

Duas notas praticas sobre a extensao:

- **nem tudo que e JavaFX precisa dela.** `SimpleStringProperty` e um bean comum e funciona
  sem toolkit; o que exige toolkit e `Control` - `Label`, `TextField`, `TableView`. Por isso o
  `SessionRecordTest` nunca precisou da anotacao e o `SessionTest` precisou ate o Label sair;
- **`onFxThread (...)` executa um trecho na thread da aplicacao e espera.** Serve para ler o
  efeito de um `Platform.runLater` pendente: como a fila e FIFO, o que voce enfileirar depois
  roda depois.

Em ambiente headless — o CI, por exemplo — a suite precisa rodar sob um X virtual:

```bash
xvfb-run --auto-servernum mvn test
```

Sem isso, `Platform.startup` falha e a extensao diz exatamente esse motivo na mensagem de
erro.

### Uma falha intermitente que nao e sua

**Qualquer classe de teste que use `@TempDir` pode falhar sem que nada esteja errado no
codigo.** Sao seis: `DatabaseTest`, `TransferManagerTest`, `TransferTest`, `SessionReaderTest`,
`SessionTest` e `ReportTesterTest`. A mensagem:

```
org.junit.platform.commons.JUnitException: Failed to close extension context
Caused by: java.io.IOException: Failed to delete temp directory C:\...\junit-<numero>.
  The following paths could not be deleted ...
  Suppressed: java.nio.file.DirectoryNotEmptyException
```

E a limpeza do `@TempDir` do JUnit no Windows: alguem ainda segura um handle do diretorio
quando o JUnit tenta apaga-lo, e a exclusao falha. **O sintoma que identifica o caso** e a linha
de resultado sair como

```
Tests run: 1452, Failures: 0, Errors: 1
```

ou seja, **zero falhas de assercao**: o teste passou e a infraestrutura tropecou depois dele.

Foi observada duas vezes durante o Passo 10, **em classes diferentes** - primeiro em
`ReportTesterTest.describesFileOnDisk`, depois em `SessionReaderTest` -, e nao reproduziu em
nenhuma das outras seis execucoes da suite no mesmo dia. A primeira versao desta secao culpava
o `ReportTesterTest`; a segunda ocorrencia mostrou que a classe e circunstancial e o que importa
e o `@TempDir`.

**Antes de investigar uma mudanca sua, rode de novo.** Se reproduzir com consistencia, ou sempre
na mesma classe, ai sim ha o que investigar.

E o mesmo conselho do erro de fork do Surefire (`Error occurred in starting fork` sem nenhuma
falha de teste): rodar de novo antes de concluir qualquer coisa.

### Cobertura por pacote — `dm3270`

**Remedida no Passo 10.** Instrucoes e ramos vem do JaCoCo; mutacao e test strength, do PIT.
Ordenada da pior cobertura para a melhor, porque e assim que serve de lista de alvos para quem
for escrever teste. Um traco em "Mutacao" significa que o pacote **nao esta** no
`targetClasses` do PIT - ver "Nem todo sobrevivente e um teste faltando", abaixo.

| Pacote | Instr. | Ramos | Mutacao | Observacao |
|---|---:|---:|---:|---|
| `dm3270.assistant` | 0% | 0% | — | UI: abas de dataset, job e transferencia |
| `dm3270.console` | 0% | 0% | — | UI, mas com parser de mensagem dentro (`ConsoleMessage`) |
| `reporter.application` | 4% | 3% | — | UI do visualizador |
| `dm3270.application` | 6% | 5% | — | UI: janelas, teclado, ciclo de vida |
| `dm3270.display` | 12% | 14% | 50% | so `FxPalette` esta no PIT; `Screen` (1.094 linhas) e as janelas nao tem teste |
| `dm3270.plugins` | 20% | 23% | 60% | so `PluginData`, `PluginField` e `ScreenLocation` no PIT; `PluginsStage` (750) fora |
| `reporter.reports` | 34% | 40% | **34%** | a pior mutacao do projeto |
| `dm3270.streams` | 53% | 46% | **38%** | `TelnetListener` e `MainframeServer` rodam headless desde o Passo 5, mas so 4 classes estao no PIT |
| `dm3270.utilities` | 54% | 70% | 87% | listado classe a classe no PIT: `Dm3270Utility`, `FileSaver`, `SiteValue` |
| `dm3270.filetransfer` | 62% | 60% | 53% | 13 classes de protocolo com teste; os 4 dialogos sao JavaFX |
| `dm3270.commands` | 64% | 51% | **50%** | subiu com o `HeadlessScreenTarget` |
| `dm3270.screen` | 64% | 51% | **50%** | o modelo de tela; listado classe a classe no PIT |
| `reporter.file` | 73% | 54% | 66% | `ReportScore` saiu do JavaFX no Passo 4 |
| `dm3270.buffers` | 79% | 75% | 77% | |
| `dm3270.datasets` | 79% | 83% | 75% | dominio saido de `database` na Onda 3 |
| `dm3270.watch` | 84% | 66% | 71% | 215/303 mutantes; os sete layouts do Passo 1 entram com 100% |
| `dm3270.structuredfields` | 84% | 65% | 63% | |
| `dm3270.extended` | 85% | 71% | 71% | |
| `dm3270.session` | 85% | 79% | 81% | saiu do JavaFX no Passo 5 |
| `dm3270.database` | 87% | 80% | 68% | inclui integracao real com SQLite |
| `reporter.record` | 87% | 86% | 80% | |
| `dm3270.telnet` | 87% | 86% | 84% | |
| `dm3270.orders` | 89% | 78% | 84% | exercitado headless |
| `dm3270.attributes` | 92% | 83% | 89% | sem JavaFX desde a troca por `TerminalColor` |
| `reporter.text` | 95% | 96% | 95% | |
| `dm3270.replyfield` | 96% | 85% | 81% | |
| `dm3270.runtime` | — | — | — | dois enums, 55 linhas |

**Total do projeto: 51% de instrucoes, 48% de ramos.** O numero reflete a camada JavaFX sem
teste, e nao a qualidade da suite: os quatro pacotes de UI no topo da tabela somam mais de 20
mil instrucoes e quase nenhum teste. Nos pacotes de protocolo a cobertura passa de 80%.

**Os quatro piores em mutacao — `reporter.reports` (34%), `streams` (38%), `commands` (50%) e
`screen` (50%) — sao onde a rede e mais fina hoje.** Nenhum deles e alvo de refatoracao por
causa disso; e informacao para quem for escrever teste.

### Cobertura por modulo — `dm3270-plugins`

| Modulo | Cobertura | Mutantes | Test strength |
|---|---:|---:|---:|
| `FanLogoff` | 100% | 40 | 100% |
| `DownloadDataset` | 82% | 221 | 74% |
| `FanLogon` | 84% | 58 | 92% |
| `ShowDataset` | 51% | 258 | 76% |
| `ShowFields` | — | — | — |

`ShowDataset` fica em 50% porque `ShowDataset.java` guarda um `DatasetStage` como campo:
instanciar o plugin exige o toolkit do JavaFX. O que da para testar ali e `Document` e
`DocumentPage`, e a cobertura reflete exatamente isso. `ShowFields` nao tem teste porque
suas tres classes sao janelas.

### Nem todo sobrevivente e um teste faltando

Um mutante pode ser **equivalente**: a alteracao no bytecode nao muda o comportamento
observavel, entao nenhum teste pode mata-lo. Ha um exemplo exato em
`EbcdicTextMaker.getStringBuilder`:

```java
if (value != 0x40 && (value < 0x4B || value == 0xFF))
  textLine.append ('.');
else
  textLine.append ((char) ebc2asc[value]);
```

O PIT troca `value < 0x4B` por `value <= 0x4B`, o que so muda o caminho tomado quando
`value` e exatamente `0x4B`. Acontece que `0x4B` em EBCDIC **e** o ponto final: o ramo
original vai para o `else` e escreve `ebc2asc[0x4B]`, que tambem e `'.'`. Os dois caminhos
produzem o mesmo caractere.

Por isso 100% de mutation score nao e a meta. Ao analisar sobreviventes, a primeira
pergunta e "existe alguma entrada que distinga os dois comportamentos?" — se nao existe, o
mutante e ruido.

Nenhum limite minimo esta configurado — as ferramentas so reportam, nao quebram o build.
Para transformar em trava depois que os numeros estabilizarem, use `jacoco:check` com uma
`<rule>` ou `<mutationThreshold>` no PIT.

---

## Mapa de modulos

### `dm3270` — 285 arquivos, 27 pacotes, 33.748 linhas

**Remedido no Passo 10.** A coluna que importa para esta refatoracao e a ultima: **headless**
quer dizer que o pacote nao nomeia JavaFX e roda sem toolkit grafico, com garantia imposta por
regra de ArchUnit que quebra a build. Quatro pacotes foram **criados** pela refatoracao e estao
marcados com †.

| Pacote | Arq. | Linhas | Responsabilidade | Headless |
|---|---:|---:|---|:---:|
| `dm3270.utilities` | 8 | 880 | Conversao EBCDIC/ASCII, empacotamento de bytes, dump hex, a porta `Site` | parcial |
| `dm3270.orders` | 15 | 1.024 | Orders do data stream (SBA, SF, SFE, RA, IC, PT, EUA, GE, FCO, MF) | sim |
| `dm3270.attributes` | 8 | 558 | Atributo de inicio de campo e atributos estendidos | sim |
| `dm3270.commands` | 12 | 1.804 | Comandos 3270: Write, Erase Write, Read, EAU, WSF, RSF, resposta AID | sim |
| `dm3270.buffers` | 8 | 291 | Bloco de bytes: escape de `0xFF`, terminador `IAC EOR` | sim |
| `dm3270.telnet` | 7 | 985 | Comandos telnet e subcomandos TN3270E | sim |
| `dm3270.extended` | 7 | 690 | TN3270E: cabecalho de comando, imagem BIND, protocolos de LU | sim |
| `dm3270.structuredfields` | 7 | 417 | Structured fields (Outbound3270DS, ReadPartition, SetReplyMode) | sim |
| `dm3270.replyfield` | 16 | 1.372 | Query replies que declaram as capacidades do terminal | sim |
| `dm3270.filetransfer` | 17 | 1.855 | IND$FILE. **13 classes de protocolo headless + 4 dialogos JavaFX** | parcial |
| `dm3270.streams` | 13 | 1.915 | Sockets, TLS, negociacao (`TelnetState`, `TerminalServer`) | **sim, desde o Passo 5** |
| `dm3270.session` | 5 | 894 | Gravacao e replay de sessoes | **sim, desde o Passo 5** |
| `dm3270.screen` † | 21 | 2.137 | **O modelo de tela e as portas** por onde o protocolo fala com ela | sim |
| `dm3270.watch` † | 17 | 1.570 | **O observador de tela** e os sete layouts de lista do ISPF | sim |
| `dm3270.datasets` † | 5 | 1.078 | **Dominio de dataset** e a porta `DatasetStore` | sim |
| `dm3270.runtime` † | 2 | 55 | **Dados neutros de execucao**: o modo e o lado da conversa | sim |
| `dm3270.database` | 16 | 1.677 | Persistencia SQLite, atras da porta `DatasetStore` | sim |
| `dm3270.plugins` | 8 | 1.635 | API de plugins. `PluginsStage` (750 linhas) e JavaFX | parcial |
| `dm3270.display` | 13 | 2.794 | **So a view**: `Screen` (1.094), `FieldManager`, `ScreenPacker`, fontes | nao |
| `dm3270.console` | 6 | 531 | Log de console. Tem parser de mensagem dentro (`ConsoleMessage`) | nao |
| `dm3270.assistant` | 19 | 2.045 | Abas de dataset, job e transferencia | nao |
| `dm3270.application` | 17 | 3.272 | Janelas, teclado, ciclo de vida. **O composition root de fato** | nao |
| `reporter.record` | 12 | 923 | Divisao de um dataset em registros: FB, VB, RDW, LF, CR, NVB, Ravel | sim |
| `reporter.text` | 3 | 267 | Interpretacao dos bytes como EBCDIC ou ASCII | sim |
| `reporter.file` | 6 | 742 | Pontuacao e escolha automatica do formato | **sim, desde o Passo 4** |
| `reporter.reports` | 8 | 834 | Geracao de relatorios (texto, hex, ASA, natload) | sim |
| `reporter.application` | 9 | 1.503 | UI do visualizador | nao |

**Os cinco pacotes de baixo — `display`, `console`, `assistant`, `application` e
`reporter.application` — sao os unicos que a regra congelada `uiIsTheOnlyPlaceThatKnowsJavaFx`
autoriza a conhecer JavaFX.** Todo o resto e vigiado por regra que quebra a build.

### `dm3270-plugins` — 12 arquivos, 5 plugins

| Modulo | Responsabilidade | Criticidade | Tem teste |
|---|---|:---:|:---:|
| `DownloadDataset` | `DocumentPage` le uma pagina de EDIT do ISPF; `Document` remonta o dataset; o plugin navega pelas paginas | **Critica** | sim |
| `ShowDataset` | Mesma logica do `DownloadDataset` mais uma janela de exibicao | **Critica** | parcial |
| `FanLogoff` | Automacao de logoff | Baixa | sim |
| `FanLogon` | Automacao de logon no servidor publico FanDeZhi | Baixa | sim |
| `ShowFields` | Inspeciona os campos da tela (ferramenta de depuracao) | Baixa | nao |

---

## A camada de display: o que ja saiu do JavaFX e o que falta

Este documento dizia que o modelo de tela era intestavel porque tudo nascia de uma
instancia de `Screen`, que estende `javafx.scene.canvas.Canvas`. Dizia tambem que extrair
o modelo da view era "o refactor de maior retorno do projeto". Foi o que a onda de
desacoplamento fez, em quatro cortes:

| Amarra | Como foi cortada |
|---|---|
| A paleta de cores era `javafx.scene.paint.Color` | `TerminalColor`, um record neutro. A conversao acontece em `FxPalette`, na hora de desenhar |
| `ScreenPosition` desenhava no `GraphicsContext` | `ScreenCanvas`, com as cinco operacoes que o desenho usa. `FxScreenCanvas` adapta |
| `ScreenContext` media a fonte por um no `Text` | `FontMetrics` guarda o resultado da medicao, nao o medidor |
| `Buffer.process` recebia a classe `Screen` | `ScreenTarget`, que estende a `DisplayScreen` que ja existia. `CursorHost` faz o mesmo pelo `Cursor` |

O resultado pratico e o `HeadlessScreenTarget`, em `test/`: uma tela com `Pen` e
`ScreenPosition` **reais** ligados a um canvas que descarta o desenho. Com ele da para
executar comandos 3270 num teste comum e verificar o texto que sobra na tela — ver
`HeadlessProcessingTest`. Antes isso era impossivel.

O que ainda falta, e por que:

- **`Screen`** continua com 1.094 linhas e oito responsabilidades. E o que mantem
  `dm3270.display` em 12%: o modelo tem teste, a classe que o hospeda nao. **Decompo-la deixou
  de ser prioridade na Onda 3**, que mediu e descobriu que os ciclos vinham dos `import` dela,
  nao do tamanho - seis cairam sem quebrar uma classe sequer.
- **`FieldManager`** ainda exige a `Screen` concreta no construtor, onde sobe uma thread
  SQLite. Por isso o `HeadlessScreenTarget` devolve zero campos, e os ramos de
  `WriteCommand.process` que dependem de haver campos nao sao percorridos. Esta escrito no
  topo da classe, nao escondido.
- **`Cursor`** ja fala com uma porta (`CursorHost`) e portanto e instanciavel headless, mas
  nenhum teste verifica comportamento de cursor — 117 mutantes, zero mortos. Por isso ficou
  fora do `targetClasses` do PIT.

Nao foi preciso TestFX nem Monocle para nada disso. O `JavaFxToolkit` existe apenas para as
classes que sao genuinamente visuais, como `Site`, cujos campos sao widgets.

---

## Cobertura atual

### `dm3270` — 51 classes de teste, 1.452 testes

**Remedida no Passo 10**, contando elementos `<testcase>` nos relatorios do Surefire, que e a
unica contagem que fecha (ver "Ler o resultado da suite", no `RELATORIO-REFATORACAO.md` §5.18):

```bash
grep -ho "<testcase" target/surefire-reports/*.xml | wc -l          # 1.452
```

| Classe de teste | Testes |
|---|---:|
| `dm3270.extended.BindCommandTest` | 79 |
| `dm3270.database.DatabaseTest` | 74 |
| `dm3270.filetransfer.TransferRecordTest` | 62 |
| `dm3270.display.PaletteFidelityTest` | 59 |
| `dm3270.display.ScreenWatcherTest` | 57 |
| `dm3270.replyfield.QueryReplyParsingTest` | 54 |
| `reporter.record.RecordMakerExtraTest` | 53 |
| `dm3270.telnet.TelnetCommandTest` | 48 |
| `dm3270.streams.TelnetStateTest` | 44 |
| `dm3270.structuredfields.StructuredFieldTest` | 44 |
| `reporter.reports.ReportMakerTest` | 43 |
| `dm3270.orders.OrderTest` | 41 |
| `dm3270.filetransfer.TransferTest` | 36 |
| `dm3270.application.SiteFormTest` | 35 |
| `dm3270.commands.AIDCommandTest` | 35 |
| `reporter.file.ReportTesterTest` | 34 |
| `dm3270.orders.ExtendedOrderTest` | 33 |
| `dm3270.screen.CursorTest` | 32 |
| `dm3270.telnet.TN3270ExtendedSubcommandTest` | 32 |
| `dm3270.filetransfer.IndFileCommandTest` | 31 |
| `dm3270.session.SessionTest` | 28 |
| `dm3270.attributes.AttributeTest` | 27 |
| `dm3270.filetransfer.TransferManagerTest` | 27 |
| `dm3270.plugins.PluginApiTest` | 27 |
| `dm3270.commands.CommandTest` | 26 |
| `dm3270.orders.BufferAddressTest` | 26 |
| `dm3270.utilities.Dm3270UtilityTest` | 26 |
| `dm3270.replyfield.QueryReplyFieldTest` | 24 |
| `reporter.text.TextMakerTest` | 23 |
| `dm3270.commands.ReadStructuredFieldCommandTest` | 22 |
| `dm3270.extended.CommandHeaderTest` | 22 |
| `reporter.record.RecordMakerTest` | 22 |
| `dm3270.session.SessionReaderTest` | 21 |
| `dm3270.attributes.StartFieldAttributeTest` | 20 |
| `dm3270.display.HeadlessProcessingTest` | 19 |
| `dm3270.session.SessionRecordTest` | 19 |
| `reporter.file.ReportScoreTest` | 17 |
| `dm3270.telnet.TelnetProcessorTest` | 16 |
| `dm3270.streams.TerminalServerTest` | 15 |
| `dm3270.architecture.LayeringTest` | 14 |
| `dm3270.display.ScreenPositionDrawingTest` | 14 |
| `dm3270.streams.TelnetSocketTest` | 14 |
| `dm3270.buffers.BufferTest` | 11 |
| `dm3270.display.ScreenContextPoolingTest` | 10 |
| `dm3270.commands.WriteControlCharacterTest` | 8 |
| `dm3270.display.ScreenDimensionsTest` | 7 |
| `reporter.application.ReportScoreViewTest` | 7 |
| `dm3270.utilities.SiteValueTest` | 6 |
| `dm3270.application.SessionRowTest` | 4 |
| `dm3270.streams.TelnetListenerHeadlessTest` | 3 |
| `dm3270.session.ParserGoldenMasterTest` | 1 |

**O `ParserGoldenMasterTest` aparece com 1 teste e e a prova central da refatoracao inteira:**
ele reprocessa uma sessao gravada e compara a saida byte a byte com um snapshot de 1.688
linhas. Um teste, e o que impede qualquer commit de mudar o que o parser produz.

**O `LayeringTest` aparece com 14** — sao as treze regras de camada mais o placar de ciclos
mutuos. E o outro lado da rede: ele nao verifica comportamento, verifica arquitetura.

### `dm3270-plugins`

| Area | Classe de teste | Testes |
|---|---|---:|
| Pagina de EDIT | `DocumentPageTest` | 34 |
| Montagem do documento | `DocumentTest` | 10 |
| Navegacao pelas paginas | `DownloadDatasetTest` | 26 |
| Pagina de EDIT (ShowDataset) | `DocumentPageTest` | 34 |
| Montagem do documento (ShowDataset) | `DocumentTest` | 10 |
| Logon automatico | `FanLogonTest` | 21 |
| Logoff automatico | `FanLogoffTest` | 29 |
| **Total** | | **164** |

`DocumentPageTest`, `DocumentTest` e `ScreenBuilder` aparecem duas vezes porque
`Document.java` e `DocumentPage.java` sao **identicos byte a byte** em `DownloadDataset`
e `ShowDataset`. A duplicacao dos testes espelha a duplicacao do codigo; ao extrair as
duas classes para um modulo comum, as copias devem ser apagadas junto.

Dois testes merecem nota:

- `TelnetProcessorTest.chunkingDoesNotChangeResult` reprocessa a mesma sessao com todos
  os tamanhos de bloco possiveis (1 byte ate o stream inteiro) e exige resultado
  identico — e a garantia de que a maquina de estados nao depende de como o socket fatia
  os dados.
- `TerminalServerTest` e `TelnetSocketTest` sobem um `ServerSocket` em loopback numa
  porta efemera e conversam com ele de verdade. Sao testes de integracao leves, mas
  cobrem o laco de leitura, o `close()` e o caminho de TLS, que nenhum mock alcancaria.

---

## Defeitos corrigidos

Estes defeitos foram encontrados escrevendo a suite. Cada um tem hoje um teste que
**exige o comportamento correto** — se a correcao for revertida, o teste falha. Nenhum
marcador `REGRESSAO`, `LIMITACAO` ou `ATENCAO` sobrou no codigo de teste.

### Corrupcao de dados e operacoes que nao funcionavam

| Onde | O defeito | A correcao |
|---|---|---|
| `FbRecordMaker.split` | O laco de aparo de nulos usava `while (reclen >= 0 && buffer[ptr2--] == 0)`. Ao consumir o registro inteiro fazia uma leitura a mais e, no primeiro registro, lia `buffer[-1]`: **qualquer dataset FB80, FB132 ou FB252 cujo primeiro registro fosse todo nulo derrubava a divisao**. | Guarda passou a `reclen > 0`. Um registro todo nulo agora vira registro de tamanho zero. |
| `DatabaseThread.deleteMember` | `delete from MEMEBERS` — a tabela se chama `MEMBERS`. O SQLite recusava, o `catch` engolia a `SQLException` e devolvia `false`: **apagar um membro do cache nunca funcionava**. | Nome da tabela corrigido. |
| `RavelRecordMaker.packRecord` | Gravava em `this.buffer` (os dados de **origem**) em vez do array que `join()` acabara de alocar. Um registro contendo `0xFF` saia zerado e corrompia a entrada; com o campo ainda nulo, lancava `NullPointerException`. | O buffer de destino passou a vir por parametro. |
| `RecordNumber (byte[], offset)` | Lia o numero com `unsignedLong (data, 2)`: sempre os bytes 2 a 5 do buffer inteiro, nao os do registro. | Le de `this.data`, a copia do proprio registro. |
| `DatabaseThread.process (MemberRequest)` | O `case FIND:` nao tinha `break` e caia no `case LIST:`, montando a lista completa do dataset a cada busca de membro. | `break` acrescentado. |
| `CrRecordMaker.split` | O incremento de `recordNumber` estava so na linha final, como pos-incremento: **todos** os registros ficavam com numero zero, e `AsaReport`/`NatloadReport`, que tratam o registro 0 de forma especial, nunca reconheciam o formato. | Incremento movido para dentro do laco. |
| `SingleRecordMaker.join` | Devolvia `new byte[0]`: reconstruir o arquivo a partir dos registros nao estava implementado. | Concatena os registros, como os outros formatos. |
| `IndFileCommand` | Os construtores `(TransferType, String, File)` e `(TransferType, String, byte[])` sempre lancavam `NullPointerException`: `setCommandText()` chamava `prefix.isEmpty()` e `prefix` so podia ser preenchido depois do construtor. | `prefix` comeca vazio, e `setPrefix (null)` e tratado como vazio. |
| `TelnetState` | `fireTelnetStateChange()` era privado e **sem nenhum chamador**: quem se registrava com `addTelnetStateListener` nunca recebia evento. | Os oito setters de estado negociado notificam os listeners. As preferencias nao, porque nao vem do host. |
| `DownloadDataset.setMax` (plugin) | Escrevia `"m"` no campo seguinte ao rotulo `Command ===>` — a linha de comando. O campo de scroll ficava intocado, entao PF7/PF8 rolava uma pagina por vez em vez de ir ao extremo, e o `"m"` sobrava para o ISPF interpretar como comando. | Passou a localizar `Scroll ===>`. |
| `DocumentPage` (plugin) | `EDIT_PATTERN` recusava telas de BROWSE e VIEW, embora `getDatasetName()` soubesse ler os quatro cabecalhos — metade daquela logica era inalcancavel. | O padrao aceita `RFEEDIT`, `EDIT`, `BROWSE` e `VIEW`. |
| `DocumentPage.getColumns` (plugin) | Localizava o cabecalho so com `findFieldContaining("columns")`: um cabecalho abreviado (`Col 1 72`) nunca era encontrado, e o ramo que compara `parts[i].equals("col")` era inalcancavel. | Procura `"columns"` e, nao achando, `"col"`. |
| `FanLogon.activate` (plugin) | `getParameter()` devolve `""` para chave ausente, nunca `null`: a verificacao `user == null \|\| password == null` nunca disparava, e uma secao `[FanDeZhi]` vazia montava o comando `TSO ` com usuario em branco. | Verificacao por `isEmpty ()`, e o plugin nao se habilita. |

### Entradas malformadas que derrubavam o parser

Um host — ou um arquivo de replay — com bytes inesperados nao deve derrubar o emulador.
Todos estes casos agora descrevem o valor recebido em vez de estourar um indice:

| Onde | Antes | Agora |
|---|---|---|
| `BindCommand.toString` | `presentationSpace` entre `0x04` e `0x79` calculava indice negativo | `Unknown (XX)` |
| `SetReplyModeSF.toString` | um modo de resposta acima de 2 estourava a tabela | `Unknown (XX) mode` |
| `UsableArea.toString` | modos de enderecamento de 5 a 14, e unidades de medida acima de 1 | `Unknown (XX)` |
| `TelnetState.setDoDeviceType` | um modelo fora de 0-5 indexava o array direto | `IllegalArgumentException` com a faixa valida, preservando a preferencia anterior |
| `TelnetCommand.process` | um `IAC WILL <tipo desconhecido>` deixava `reply[1]` em `0x00` e o construtor da propria resposta recusava o buffer | responde `DONT`: uma opcao desconhecida nao pode ser aceita |
| `TN3270ExtendedSubcommand.process` | um subtipo que o construtor nao reconhecia deixava `subType` nulo, e `switch` sobre enum nulo lancava `NullPointerException` | registra o byte e sai sem processar |
| `TN3270ExtendedSubcommand.doesFunction` | `NullPointerException` fora dos subcomandos FUNCTIONS | `false` |
| `ReadPartitionSF.brief` | `getReply ().get ()` sem checar `isPresent ()` | descreve o comando embrulhado |
| `DataRecord.getText` | lia `buffer[buffer.length - 1]` num registro vazio; e usava `% 0xFF` no lugar de `& 0xFF` | devolve `""`, e o teste do ultimo byte usa `& 0xFF` |
| `QueryReplyField.toString` e `Summary.toString` | `NullPointerException` fora do modo replay, onde a lista `replies` nao esta preenchida | informam `no summary` |
| `CharacterSets` | um `descriptorLength` zerado prendia o laco de descritores **para sempre** | exige avanco positivo e nao le alem do buffer |
| `DistributedDataManagement` | um tamanho de subcampo zerado prendia o laco **para sempre** | para quando o tamanho nao garante avanco |
| `RecordTester.getPreferredTextMaker` | lista vazia caia em `textTesters.get (0)` e estourava; uma amostra vazia devolvia `NaN`, que nunca e maior que `-1.0`, e nenhum candidato era escolhido | `IllegalStateException` explicando o que falta; `NaN` e ignorado na comparacao |

### Falhas silenciosas

| Onde | O defeito | A correcao |
|---|---|---|
| `TerminalServer.run` | Uma falha antes de a conexao subir (destino inacessivel, handshake TLS recusado) estourava **antes** de `running` virar `true`, e o `catch` so agia quando ele era verdadeiro: nem stack trace, nem `close()`, nem aviso. A interface ficava achando que o terminal conectou. | Uma flag `connected` separa "nunca conectou" de "caiu no desligamento". No primeiro caso reporta e fecha o listener. |
| `TelnetState.setLastAccess` | `lastAccess` so era criado dentro de `run()`, e o metodo nao se protegia: contabilizar uma leitura antes de a thread subir lancava `NullPointerException`. | Criado na construcao. |
| `CacheEntry.replace` | A assercao era `dataset.getName ().equals (dataset.getName ())` — o parametro comparado consigo mesmo, nunca falhava. | Compara o nome guardado com o do parametro. |
| `ErrorRecord` | O construtor de escrita nao preenchia `errorText`: o `toString` terminava em `- null`. | Os dois construtores usam a mesma descricao. |
| `ImplicitPartition` | O construtor `(rows, columns)` montava o buffer mas nao alimentava `width`/`height`: o relatorio de uma reply recem-criada mostrava zeros. | Preenche tambem os campos de leitura. |
| `DatabaseThread.createDatasetList` | Um filtro vazio ia para o ramo do nome exato e devolvia sempre lista vazia. | Vazio ou `*` lista tudo, `PREFIXO*` lista a faixa, um nome sem curinga procura o exato. |

### Formatacao dependente de plataforma

| Onde | O defeito | A correcao |
|---|---|---|
| `Dm3270Utility.toHex` e `Record.toHex` | Removiam **um** caractere para tirar a quebra final; no Windows o separador tem dois e sobrava um `\r`. | Removem o separador inteiro. |
| `Dm3270Utility.toHexString (byte[])` | Nao removia o separador final, ao contrario da versao de tres argumentos: todo `toString` de `TransferRecord` terminava com um espaco sobrando. | Delega para a versao de tres argumentos. |
| `BindCommand.toString` | Dividia o relatorio de LU com `split ("\n")`, deixando o `\r` do Windows **no meio** da linha de duas colunas — num terminal o `\r` devolve o cursor ao inicio e a coluna secundaria apagava a primaria. | Divide com `split ("\\R")`, que consome o separador inteiro. |

### Acoplamento com a camada JavaFX

Tres classes dependiam de `Screen` ou do toolkit para chegar a um unico valor. Trocar a
dependencia pelo valor destravou a logica inteira — e a cobertura mostra o efeito:

| Onde | Antes | Agora | Cobertura |
|---|---|---|---:|
| `TransferManager` | recebia `Screen` para ler `screen.getPrefix ()` em duas linhas | recebe `Supplier<String>`; `Screen` passa `this::getPrefix` | 4% -> 87% |
| `FileTransferOutboundSF.process` | `screen.getTransferManager ()` na primeira linha | `process (TransferManager)` recebe o gerenciador; `process (Screen)` delega | 19% -> 43% |
| `DownloadDataset.saveDocument` (plugin) | abria um `FileChooser` dentro de `Platform.runLater ()`, o que impedia testar o fim da captura | a escolha do arquivo e um `Consumer<Document>` substituivel; `writeTo` grava | 60% -> 82% |
| `FanLogon` (plugin) | `showAlert` instanciava `Alert` direto | o alerta e um `BiConsumer` substituivel | 75% -> 84% |

### Comportamentos que **nao** eram defeitos

Dois casos que os testes tratavam como suspeitos e que, verificados, estao corretos. Os
testes seguem lá, agora descrevendo a regra em vez de alertar sobre ela:

- **`TelnetState.doesEOR()` e `doesBinary()` fazem `OR` com `does3270Extended`.** A RFC
  2355 define o TN3270E sobre transmissao binaria com marcacao de fim de registro: um
  host que negociou TN3270E ja concordou com as duas. Desligar EOR com o TN3270E ligado
  nao deve ter efeito — e o `OR` e o que garante isso.
- **`Document` e `DocumentPage` duplicados nos dois plugins.** Os arquivos continuam
  identicos byte a byte em `DownloadDataset` e `ShowDataset`, e as correcoes foram
  aplicadas nos dois. Extrair um modulo comum mudaria o empacotamento: cada plugin e um
  jar solto que o dm3270 carrega, e um jar compartilhado a mais teria de ser distribuido
  junto. E o refactor certo, mas e uma decisao de distribuicao, nao de teste.

---

## Proximos alvos

**Quatro dos seis alvos que esta lista trazia foram feitos.** Ficam registrados riscados,
porque a ordem em que cairam e o argumento de que o metodo funciona:

1. ~~**Fake de `Pen` e `DisplayScreen`.**~~ Feito na Onda 1 e ampliado depois: hoje e o
   `HeadlessScreenTarget`, que usa um `Pen` e um vetor de `ScreenPosition` **reais** e monta
   campos de verdade. Desde o Passo 5 tambem implementa `SessionDisplay`.
2. ~~**Extrair o modelo da view em `dm3270.display`.**~~ Feito na Fase 2: o modelo de tela
   virou o pacote `screen`, com zero JavaFX e regra de camada propria. `display` ficou sendo
   so a view.
3. ~~**`ReportScore` sem JavaFX.**~~ Feito no Passo 4, e rendeu mais do que esta linha previa:
   a porta `reports.ReportContext` desfez o ciclo `reporter.file <-> reporter.reports`.
4. ~~**`SessionRecord` e `Session`.**~~ Feito no Passo 5, e tambem rendeu mais: desfez o ciclo
   `session <-> streams`, que o relatorio dizia depender do composition root.

O que continua aberto, em ordem de retorno medido:

1. **O composition root.** Continua sendo o maior item estrutural que sobra, e o mais caro -
   mas **nao e mais o unico caminho, e esta lista ja disse que era**. Ela afirmava que "seis
   dos doze ciclos restantes esperam por ele"; o Passo 6 derrubou tres desses seis sem
   toca-lo, medindo aresta por aresta. Hoje sao **nove ciclos**, dos quais cinco sao inerentes
   ao 3270, um e do `reporter` e foi descartado com o usuario, e **tres sao acidentais**:

   - `filetransfer <-> screen`, que espera `getTransferManager ()` sair do `ScreenTarget`;
   - `commands <-> filetransfer`, que **nao** espera isso - este arquivo, o `LayeringTest` e o
     proprio `TODO` da `ScreenTarget` diziam que sim ate o Passo 10, e a medicao desmentiu: sao
     quatro construcoes que nunca tocam a tela;
   - `streams <-> telnet`, o unico dos tres que e mesmo estrutural - e por `TelnetState`
     atravessando as duas pontas, nao pelo enum `Function`, cujos "~30 call sites" sao **tres
     linhas**.

   Antes de aceitar que um ciclo depende de um refactor grande, rode os dois `grep` de import
   entre os dois pacotes e veja quais tipos sustentam cada direcao. **Cuidado com um detalhe
   que so apareceu no Passo 10:** um `grep` de import conta **11** ciclos e o ArchUnit conta 9,
   porque `AIDCommand.AID_ENTER` e `StructuredField.QUERY_REPLY` sao constantes de compilacao,
   que o javac embute no chamador e somem do bytecode.
2. **`TransferManager`** — o resto do fluxo de IND$FILE depende de `Screen`; extrair a
   parte de estado tornaria testavel o ciclo abrir/transferir/fechar.
3. **O caminho de lancamento**: `Console`, `OptionStage` e `ConsoleKeyPress` **nao tem teste
   nenhum**. Sao o ultimo item da Onda 2 e a Onda 4 do diagnostico, e caracterizar vem antes
   de qualquer coisa. Medido: o `Console` alcanca **dez campos package-private de widgets** do
   `OptionStage`, que e 100% da superficie de pacote daquela classe.
4. **`TelnetListener` no `targetClasses` do PIT.** Ele ganhou os tres primeiros testes da sua
   historia no Passo 5, mas ficou de fora da lista pelo mesmo criterio do `ScreenWatcher`:
   uma classe grande com dois caminhos cobertos entra com sobreviventes demais para o numero
   significar alguma coisa. Entra quando o tratamento de subcomandos telnet tiver rede.
5. **Os quatro pacotes de pior mutacao**, pela tabela de cobertura acima:
   `reporter.reports` (34%), `streams` (38%), `commands` (50%) e `screen` (50%). Nao sao alvo de
   refatoracao - sao alvo de teste.
6. Corrigir os defeitos do [BACKLOG-DEFEITOS.md](BACKLOG-DEFEITOS.md) e trocar os testes que
   documentam o comportamento atual por testes que exigem o comportamento correto. **Isso e
   decisao do time, nao tarefa aprovada**, e nao pertence a branch da refatoracao (Regra 1).

**E um alvo que saiu desta lista no Passo 10:** a limpeza do codigo provadamente morto (Onda 6
do diagnostico) foi feita - oito blocos `if (false)`, quatro `if (true)`, o enum
`BuildInstruction`, quatro membros sem chamador do `FieldManager` e o `application/Terminal.java`
inteiro. Sobraram de proposito **o `DatasetCache`** (provadamente morto, mas remove-lo levaria a
suite de 1.452 para 1.445 e encolheria o denominador do PIT) e **as ~356 a 459 linhas de codigo
comentado**, cuja maior concentracao e `telnet/TelnetProcessor.java`, que e coberto pelo golden
master.
