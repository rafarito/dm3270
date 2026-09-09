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
ficou defasado entre a Onda 1 e o Passo 5. As secoes abaixo foram **remedidas no Passo 5**:
"Situacao atual", "Rede de seguranca", "Testes que exigem JavaFX" e "Proximos alvos".

As tabelas de **cobertura por pacote e por modulo**, o **mapa de modulos** e a lista de
**defeitos corrigidos** sao instantaneos mais antigos: as ordens de grandeza continuam
valendo, os numeros exatos nao. Regenere com `mvn clean test` e
`mvn test-compile pitest:mutationCoverage` antes de citar qualquer um deles.

O estado corrente da refatoracao - o que falta, o que foi decidido e por que - esta no
`CLAUDE.md` e no `RELATORIO-REFATORACAO.md`, que nao sao versionados.

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

Medido ao fim do Passo 6.

| | `dm3270` | `dm3270-plugins` |
|---|---:|---:|
| Testes | 1.452 | 248 |
| Cobertura de instrucoes (projeto todo) | 51% | — |
| Mutantes gerados | 4.020 | — |
| Mutation coverage | 66% | — |
| **Test strength** | **86%** | — |

**Os quatro numeros do PIT e do JaCoCo sao identicos aos do Passo 5, e isso e o esperado.** O
Passo 6 nao acrescentou teste de comportamento nenhum e nao mexeu no `targetClasses`: os dois
testes a mais sao as duas regras de camada novas, que o ArchUnit conta como teste e o PIT nao
enxerga. Um passo que so corta acoplamento move o placar de ciclos e nao move o de cobertura -
e reconhecer isso e o que impede de procurar um ganho que nao existe.

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

| Pacote | Cobertura | Observacao |
|---|---:|---|
| `dm3270.replyfield` | 97% | |
| `reporter.text` | 93% | |
| `reporter.record` | 88% | |
| `dm3270.database` | 87% | inclui integracao real com SQLite |
| `dm3270.attributes` | 91% | sem JavaFX desde a troca por `TerminalColor` |
| `dm3270.telnet` | 87% | |
| `dm3270.orders` | 90% | ja usava `DisplayScreen`; agora exercitado headless |
| `dm3270.extended` | 85% | |
| `dm3270.structuredfields` | 84% | |
| `dm3270.buffers` | 80% | |
| `dm3270.filetransfer` | 62% | os quatro dialogos sao JavaFX |
| `dm3270.streams` | 52% | `MainframeServer` e `TelnetListener` sao JavaFX |
| `dm3270.commands` | 61% | subiu com o `HeadlessScreenTarget`; `Profile` ainda e JavaFX |
| `reporter.file` | 42% | `ReportScore` instancia `TextArea` no construtor |
| `dm3270.utilities` | 60% | subiu com o `SiteFormTest` (chamava-se `SiteTest` ate o Passo 3) |
| `reporter.reports` | 30% | `createPages` e `getFormattedRecord` recebem `ReportScore` |
| `dm3270.session` | 24% | `Session` e `SessionRecord` sao JavaFX |
| `dm3270.plugins` | 21% | `PluginsStage` e JavaFX |
| `dm3270.display` | 10% | o modelo tem teste; `Screen` (1.010 linhas) e as janelas ainda nao |
| `dm3270.application`, `dm3270.assistant`, `dm3270.console`, `reporter.application` | 0% | UI |

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

### `dm3270` — 228 arquivos, 25 pacotes

| Pacote | Responsabilidade | Criticidade | Tem teste |
|---|---|:---:|:---:|
| `dm3270.utilities` | Conversao EBCDIC/ASCII, empacotamento de bytes, dump hex, caminhos de gravacao | **Critica** | sim |
| `dm3270.orders` | Orders do data stream (SBA, SF, SFE, RA, IC, PT, EUA, GE, FCO, MF) e enderecamento de 12/14 bits | **Critica** | sim |
| `dm3270.attributes` | Atributo de inicio de campo e atributos estendidos (cor, destaque) | **Critica** | sim |
| `dm3270.commands` | Comandos 3270: Write, Erase Write, Read, EAU, WSF, RSF e a resposta AID | **Critica** | sim |
| `dm3270.buffers` | Encapsulamento telnet: escape de `0xFF`, terminador `IAC EOR` | **Critica** | sim |
| `dm3270.telnet` | Maquina de estados do stream, comandos telnet e subcomandos TN3270E | **Critica** | sim |
| `dm3270.extended` | TN3270E: cabecalho de comando, imagem BIND, protocolos de LU | Alta | sim |
| `dm3270.structuredfields` | Structured fields (Outbound3270DS, ReadPartition, SetReplyMode, EraseReset) | Alta | sim |
| `dm3270.replyfield` | Query replies que declaram as capacidades do terminal | Alta | sim |
| `dm3270.filetransfer` | IND$FILE: parsing do comando, registros de transferencia, montagem do arquivo | Alta | sim |
| `dm3270.plugins` | API exposta a plugins externos (`PluginData`, `PluginField`, `ScreenLocation`) | Alta | sim |
| `dm3270.streams` | Sockets, TLS, negociacao (`TelnetState`, `TerminalServer`, `TelnetSocket`) | Alta | sim |
| `dm3270.database` | Cache SQLite de datasets e membros | Media | sim |
| `dm3270.session` | Gravacao e replay de sessoes | Media | parcial (`SessionReader`) |
| `dm3270.display` | Modelo da tela: `Screen`, `Field`, `FieldManager`, `Cursor`, `Pen`, `ScreenPacker` | **Critica** | **nao** |
| `dm3270.console` | Log de console | Baixa | nao |
| `dm3270.assistant` | Abas de datasets, jobs e transferencias (JavaFX) | Baixa | nao |
| `dm3270.application` | Janelas, teclado, ciclo de vida (JavaFX) | Baixa | nao |
| `reporter.record` | Divisao de um dataset em registros: FB, VB, RDW, LF, CR, CR/LF, NVB, Ravel | Alta | sim |
| `reporter.text` | Interpretacao dos bytes como EBCDIC ou ASCII, e deteccao do formato | Alta | sim |
| `reporter.file` | Pontuacao e escolha automatica do formato de um arquivo | Media | parcial |
| `reporter.reports` | Geracao de relatorios (texto, hex, ASA, natload) | Media | parcial |
| `reporter.application` | UI do visualizador (JavaFX) | Baixa | nao |

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

- **`Screen`** continua com 1.010 linhas e oito responsabilidades. E o que mantem
  `dm3270.display` em 10%: o modelo tem teste, a classe que o hospeda nao.
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

### `dm3270`

| Area | Classe de teste | Testes |
|---|---|---:|
| Conversao e empacotamento | `Dm3270UtilityTest` | 26 |
| Enderecamento de buffer | `BufferAddressTest` | 26 |
| Orders do data stream | `OrderTest` + `ExtendedOrderTest` | 74 |
| Atributos | `AttributeTest` + `StartFieldAttributeTest` | 47 |
| Encapsulamento telnet | `BufferTest` | 11 |
| Stream telnet | `TelnetProcessorTest` | 16 |
| Comandos telnet | `TelnetCommandTest` | 48 |
| Subcomandos TN3270E | `TN3270ExtendedSubcommandTest` | 32 |
| Comandos 3270 | `CommandTest` + `WriteControlCharacterTest` | 34 |
| Resposta AID | `AIDCommandTest` | 35 |
| Resposta de capacidades | `ReadStructuredFieldCommandTest` | 22 |
| Cabecalho TN3270E | `CommandHeaderTest` | 22 |
| Imagem BIND | `BindCommandTest` | 79 |
| Campos estruturados | `StructuredFieldTest` | 44 |
| Query replies | `QueryReplyFieldTest` + `QueryReplyParsingTest` | 78 |
| IND$FILE | `IndFileCommandTest` | 31 |
| Registros de transferencia | `TransferRecordTest` | 62 |
| Estado da transferencia | `TransferTest` | 36 |
| Ciclo da transferencia | `TransferManagerTest` | 27 |
| API de plugins | `PluginApiTest` | 27 |
| Dimensoes de tela | `ScreenDimensionsTest` | 7 |
| Estado da sessao telnet | `TelnetStateTest` | 44 |
| Socket do mainframe | `TerminalServerTest` + `TelnetSocketTest` | 25 |
| Arquivos de replay | `SessionReaderTest` | 21 |
| Cache SQLite | `DatabaseTest` | 61 |
| Registros (reporter) | `RecordMakerTest` + `RecordMakerExtraTest` | 75 |
| Texto EBCDIC/ASCII (reporter) | `TextMakerTest` | 23 |
| Formatos de relatorio (reporter) | `ReportMakerTest` | 43 |
| Escolha do formato (reporter) | `ReportTesterTest` | 34 |
| **Total** | | **1.110** |

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
   ao 3270, um e do `reporter` e foi descartado com o usuario, e **tres sao acidentais**: dois
   de `getTransferManager ()` no `ScreenTarget` e `streams <-> telnet`, o unico dos tres que e
   mesmo estrutural. Antes de aceitar que um ciclo depende de um refactor grande, rode os dois
   `grep` de import entre os dois pacotes e veja quais tipos sustentam cada direcao.
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
5. Corrigir os defeitos do [BACKLOG-DEFEITOS.md](BACKLOG-DEFEITOS.md) e trocar os testes que
   documentam o comportamento atual por testes que exigem o comportamento correto. **Isso e
   decisao do time, nao tarefa aprovada**, e nao pertence a branch da refatoracao (Regra 1).
