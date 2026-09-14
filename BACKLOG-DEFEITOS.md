# Backlog de defeitos

Defeitos reais encontrados durante a refatoração estrutural e **deliberadamente não
corrigidos** nela.

A refatoração tem uma regra: preservar 100% do comportamento observável. Corrigir qualquer
item desta lista mudaria o resultado de alguma operação, então cada um foi mantido como
está — inclusive quando o código novo teve de reproduzir o defeito de propósito. Isso separa
duas decisões que não deveriam se misturar: *reorganizar* e *mudar o que o programa faz*.

Cada item abaixo é uma decisão pendente, não uma tarefa aprovada. Corrigir é uma escolha do
time, num commit `fix(...)` próprio, com teste que falha antes e passa depois.

---

## 1. `Console.setModel` — `case 5` sem `break`

**Arquivo:** [application/Console.java:196-200](src/com/bytezone/dm3270/application/Console.java#L196-L200)

```java
case 5:
  alternateScreenDimensions = new ScreenDimensions (27, 132);
  telnetState.setDoDeviceType (5);
                                     // <-- falta break
default:
  logger.warn ("Invalid model number: {}", model);
```

Um terminal modelo 5 (27x132, perfeitamente válido) é configurado corretamente e **em
seguida** loga `"Invalid model number: 5"`.

**Efeito hoje:** apenas ruído no log. O modelo 5 funciona.

**Por que importa:** se alguém adicionar tratamento ao `default` — um alerta, um fallback,
um `return` — o modelo 5 passa a quebrar de verdade. O defeito está armado.

**NÃO está congelado, ao contrário do que esta linha afirmava.** Até o Passo 8 ela dizia
"Congelado por `ScreenDimensionsTest` e a caracterização de `setModel`". Medido com o arquivo
aberto: `grep -rn setModel test/` não devolve nada, e o `ScreenDimensionsTest` nunca constrói
um `Console` — ele cobre a classe `ScreenDimensions`, que é outra coisa. Não havia, e não há
até o commit que extrai o `runtime.TerminalModel`, nenhum teste sobre este `switch`.

**A congelar** pelo `TerminalModelTest` e pelo `setModel` reescrito, ainda neste passo: o
modelo 5 configura 27x132 **e então** reclama, e o caminho inválido não atribui
`alternateScreenDimensions`.

---

## 2. `ShowDataset` não reconhece marcadores ISPF em maiúsculas

**Repositório:** `dm3270-plugins` · **Arquivo:** `ShowDataset/src/com/bytezone/plugins/DocumentPage.java`

O `DownloadDataset` foi endurecido para reconhecer os marcadores de forma robusta:

```java
// DownloadDataset — por substring, sem diferenciar caixa
if (bannerValue.regionMatches (true, 0, START_DATA, 0, START_DATA.length ()))
String upperVal = val.toUpperCase (Locale.ROOT);
if (!hasBeginning && upperVal.contains (TOP_MARKER))
```

O `ShowDataset` ficou para trás:

```java
// ShowDataset — por prefixo, sensível a caixa
if (nextField.getFieldValue ().startsWith (START_DATA))
if (!hasBeginning && val.contains ("Top of Data"))
```

Hosts que exibem os marcadores em maiúsculas ou com prefixo
(`AUTOSAVE *** TOP OF DATA ***` — comum em ISPF sob Hercules/MVS) funcionam no download e
falham no `ShowDataset`.

**Nota importante:** isto **não** é argumento para unificar as duas cópias. A duplicação
entre módulos de plugin é proposital — cada plugin é independente e pode implementar a mesma
ideia de formas diferentes. A correção, se for feita, é aplicar o mesmo endurecimento na
cópia do `ShowDataset`, mantendo-a separada.

**Testes que faltam junto:** o `DownloadDataset` tem dois casos para marcadores em
maiúsculas (com e sem prefixo `AUTOSAVE`) que o `ShowDataset` não tem.

---

## 3. `DefaultReportMaker` devolve a string `"Not possible"`

**Arquivo:** [reporter/reports/DefaultReportMaker.java:50-72](src/com/bytezone/reporter/reports/DefaultReportMaker.java#L50-L72)

```java
@Override
public String getFormattedRecord (ReportScore reportScore, Record record)
{
  return "Not possible";        // string mágica no lugar de dado
}

@Override
public boolean test (Record record, TextMaker textMaker)
{
  return false;
}
```

Uma subclasse que esqueça de sobrescrever produz relatórios com a literal `"Not possible"`
no lugar do conteúdo — sem exceção, sem log, sem falha visível até alguém abrir o relatório.
O `test()` devolvendo `false` por padrão faz um `ReportMaker` mal configurado nunca ser
escolhido pelo scoring, também em silêncio.

**Correção sugerida:** declarar os métodos `abstract` na classe base. Se alguma implementação
genuinamente não suporta formatação parcial, o contrato deve expor isso
(`boolean supportsPartialRecords ()`) ou lançar `UnsupportedOperationException` — nunca
devolver uma string que se parece com dado.

---

## 4. Implementações no-op que quebram o contrato

| Local | Código | Problema |
|---|---|---|
| [display/HistoryScreen.java:108-111](src/com/bytezone/dm3270/display/HistoryScreen.java#L108-L111) | `public void insertCursor (int position) { }` | Declara `implements DisplayScreen` mas ignora o método, porque uma tela de histórico é imutável |
| [structuredfields/StructuredField.java:43](src/com/bytezone/dm3270/structuredfields/StructuredField.java#L43) | `public void process (Screen screen) { }` | O cliente chama e acredita que processou |
| [buffers/DefaultBuffer.java:17](src/com/bytezone/dm3270/buffers/DefaultBuffer.java#L17) | `logger.warn ("Nothing to process")` | Único sinal é um WARN no log |

**Correção sugerida:** segregar o que é processável do que não é. `HistoryScreen` deveria
implementar uma `ReadOnlyScreen` (subconjunto sem `insertCursor`/`clearScreen`), e
`DisplayScreen` estender essa. `StructuredField` deveria ser abstrata sem implementação de
`process`, forçando cada subclasse a decidir.

---

## 5. 94 `assert` ativos como validação

`assert` está **desligado por padrão na JVM**. Em produção estas verificações não existem —
o `dm3270` roda com o `java -jar` normal, sem `-ea`.

**A contagem foi corrigida no Passo 10.** Este item dizia 103, que é o número do diagnóstico
original e conta as ocorrências do texto `assert` no `src/`. Medido:

```bash
grep -rn '^\s*assert ' src/ --include=*.java | wc -l        # 94, ativos
grep -rn '^\s*//\s*assert ' src/ --include=*.java | wc -l    # 9, comentados
```

São **94 ativos**; os outros 9 estão comentados e não fazem nada. A decisão pendente é sobre
os 94.

Exemplos em [plugins/PluginsStage.java](src/com/bytezone/dm3270/plugins/PluginsStage.java#L322):

```java
assert !screen.isKeyboardLocked ();
assert consolePane != null;
```

**Por que não foi mexido na refatoração:** o Surefire roda com `enableAssertions` ligado
([pom.xml:114](pom.xml#L114)), então os `assert` **estão ativos durante os testes**.
Convertê-los para `Objects.requireNonNull` ou `IllegalStateException` mudaria o
comportamento nos dois ambientes de uma vez — em produção passariam a existir, nos testes
mudariam de tipo de exceção. É uma mudança de comportamento real, não uma reorganização.

**Decisão pendente:** converter em validação de verdade, remover, ou passar a rodar a
aplicação com `-ea`.

---

## 6. `Site.getPort` e `Site.getModel` corrigem o widget silenciosamente

**Arquivo:** [utilities/Site.java:61-112](src/com/bytezone/dm3270/utilities/Site.java#L61-L112)

```java
public int getPort ()
{
  try {
    int portValue = Integer.parseInt (port.getText ());
    if (portValue <= 0) {
      logger.warn ("Invalid port value: {}", port.getText ());
      port.setText ("23");        // getter mutando a UI
      portValue = 23;
    }
    return portValue;
  } catch (NumberFormatException e) {
    port.setText ("23");
    return 23;
  }
}
```

Três problemas de uma vez:

1. **Getter com efeito colateral.** Ler a porta duas vezes produz efeitos diferentes na
   primeira e na segunda chamada. `toString()` chama `getPort()`, então **imprimir um Site
   com porta inválida altera o Site**.
2. **`setText` fora da JavaFX Application Thread.** Este getter é chamado a partir de código
   de rede. Mexer em widget fora da thread da UI é comportamento indefinido.
3. **Validação silenciosa.** Regras de domínio (porta > 0, modelo entre 2 e 5) vivem dentro
   de getters de UI e falham com um `logger.warn` que ninguém lê.

**Congelado por:** `SiteTest`, que tem um teste dedicado só ao efeito colateral da segunda
leitura.

---

## 7. Campos públicos mutáveis atravessando fronteira de thread

| Classe | Campos públicos mutáveis |
|---|---|
| [DatabaseRequest](src/com/bytezone/dm3270/database/DatabaseRequest.java#L31-L33) | `result`, `databaseName`, `databaseUpdated` |
| [DatasetRequest](src/com/bytezone/dm3270/database/DatasetRequest.java#L9-L11) | `dataset`, `datasetName`, `datasets` |
| [MemberRequest](src/com/bytezone/dm3270/database/MemberRequest.java#L9-L13) | `member`, `memberName`, `dataset`, `datasetName`, `members` |

O `DatabaseThread` escreve nesses campos na sua própria thread e a thread da UI os lê depois
de `processResult()`, **sem `volatile` nem sincronização**. A entrega via `BlockingQueue`
estabelece uma barreira de memória na ida, mas não na volta.

É uma corrida de dados real. Não se manifesta com frequência porque a JVM em x86 raramente
reordena essas leituras, mas não há garantia nenhuma pelo modelo de memória do Java.

**Nota:** a refatoração da onda 3 torna esses campos privados e devolve respostas imutáveis,
o que fecha a corrida — mas isso é consequência da reorganização, não uma correção
deliberada. Se a onda 3 não acontecer, o defeito continua.

---

## 8. `UploadDataset` não é publicado nas releases de plugin

**Repositório:** `dm3270-plugins` · **Arquivo:** `.github/workflows/release.yml`

```yaml
for plugin in DownloadDataset FanLogoff FanLogon ShowDataset ShowFields; do
```

São cinco plugins. O `UploadDataset` existe, tem 981 linhas e a melhor cobertura de teste do
repositório — e **nunca é empacotado numa release**. Quem baixa os JARs da release não
recebe o upload.

O workflow também compila com `javac` direto sobre `$plugin/src/com/bytezone/plugins/*.java`,
ignorando o Maven que o repositório configura — então a lista de módulos existe em dois
lugares que podem divergir, e divergiram.

---

## 9. O layout de tracos da lista de dataset nao persiste o volume

**Arquivo:** [watch/ScreenWatcher.java](src/com/bytezone/dm3270/watch/ScreenWatcher.java) —
`addDataset`, `case 5`

O `addDataset` mantem dois objetos em paralelo: o `DatasetSummary`, que alimenta a tabela do
assistant, e o `Dataset`, que vai para o `DatasetStore`. Cada ramo do `switch` preenche os
dois. Menos um.

```java
case 3:                                     // Message e Volume
  dataset.setVolume (rowFields.get (2).getText ().trim ());
  ds.setVolume (dataset.getVolume ());      // <- persiste

case 4:                                     // Catalog, tres linhas
  dataset.setVolume (rowFields.get (2).getText ().trim ());
  ...
  ds.setVolume (dataset.getVolume ());      // <- persiste

case 5:                                     // tracos, duas linhas
  dataset.setVolume (rowFields.get (2).getText ().trim ());
  if (rowFields.size () >= 6)
  {
    ...
    ds.setSpace (...);
    ds.setDisposition (...);                // <- e so
  }
```

No `screenType 5` o `ds.setVolume` **nao existe**. O volume e lido da tela, aparece na tabela
do assistant e nunca chega ao banco — enquanto espaco, disposicao e datas do mesmo dataset,
lidos da mesma tela, chegam. O `ds.setDevice` tambem falta, mas ali o `case 2` tambem nao o
chama, entao nao ha assimetria.

O efeito e silencioso: quem consultar o banco depois ve o dataset com a coluna de volume
vazia, sem nenhum sinal de que a tela tinha o dado. E so acontece nesse layout, o que torna o
sintoma dependente de qual formato de DSLIST o host produz.

**Nota:** `ScreenWatcherTest.storesSpaceAndDispositionButNotTheVolume` e
`threeFieldsFillOnlyTheVolume` congelam o comportamento atual, com o porque escrito no proprio
teste. Corrigir e acrescentar uma linha; o que o teste garante e que a correcao seja
deliberada, e nao um efeito colateral da decomposicao em Strategy.

---

## 10. O cache do `DatabaseThread` é escrito e nunca lido

**Arquivo:** [database/DatabaseThread.java](src/com/bytezone/dm3270/database/DatabaseThread.java)
— o campo `cache`, e [database/CacheEntry.java](src/com/bytezone/dm3270/database/CacheEntry.java)

```java
private final Map<String, CacheEntry> cache = new TreeMap<> ();
```

São **onze escritas e nenhuma leitura**, medidas no Passo 10 e espalhadas por cinco classes
(`DatabaseCommands`, `DatasetCommands`, `DatasetRepository`, `MemberCommands`,
`MemberRepository`). O "treze" que este item trazia era o número de trechos dentro do
`DatabaseThread` **antes** da decomposição do Passo 2. Todo `cache.get` existe apenas para
decidir entre `put`, `replace` e `putMember` — nenhuma requisição é respondida a partir do
cache.

A prova de que é só escrita é estrutural, e não estatística: os **sete** métodos do
`DatasetCache` são **todos `void`**, e o `Map<String, CacheEntry> entries` é `private` e não
tem *getter*. Não existe caminho pelo qual um valor saia dali.
`findDataset` e `findMember` vão ao banco todas as vezes, mesmo quando a entrada está lá; o
valor devolvido por `CacheEntry.addMember` é descartado no único lugar que o chama.

O efeito é uma estrutura que cresce sem limite durante a sessão — uma entrada por dataset visto,
com um mapa de membros dentro — e que nunca é consultada. Não há erro de resultado: há custo de
memória e de leitura de código, e um `CacheEntry` inteiro cuja razão de existir não se sustenta.

Vale dizer o que **não** é: não é um cache quebrado que devolve dado velho. É um cache que
ninguém pergunta. Se a intenção original era evitar ida ao banco, o que falta é o `findDataset`
consultá-lo antes do `select` — e aí passaria a haver invalidação a pensar, o que é outra
conversa.

**Nota:** a remoção pertencia à onda de limpeza, e o **Passo 10 a mediu e o usuário decidiu
deixar o cache onde está.** O motivo é o preço, e ele é real: remover arrasta o `CacheEntry` e
os **sete testes** da classe aninhada `Cache` do `DatabaseTest` (99 linhas), levando a suíte de
1.452 para 1.445 e encolhendo o denominador do PIT em `database.*`. Perder denominador é a
forma mais silenciosa de afrouxar a rede, e a Regra 5 existe justamente contra isso.

Ou seja: **este item continua sendo uma decisão pendente do time, e agora com o custo
medido.** O que a decomposição do `DatabaseThread` fez foi juntar as onze escritas num
colaborador só, para que a decisão seja de uma linha em vez de uma investigação.

Para o PIT, o `DatasetCache` entra com **5 mutantes e 0 mortos**, e isso **não é para
consertar**: não há o que asseverar sobre um cache que ninguém lê.

---

## 11. `createMemberList` monta SQL inválido com curinga no meio do nome

**Arquivo:** [database/DatabaseThread.java](src/com/bytezone/dm3270/database/DatabaseThread.java)
— `createMemberList`

```java
String query = "select * from MEMBERS where DATASET='" + request.datasetName + "'";
int pos = request.memberName.indexOf ('*');

if (pos > 0)
{
  ...
  query += "where NAME>='" + from + "' and NAME<='" + to + "'";   // <- segundo where
}
```

O segundo `where` deveria ser `and`. Com `memberName` = `"IEF*"` a query sai como
`select * from MEMBERS where DATASET='X' where NAME>='IEF' and NAME<='IEFZ'`, o SQLite recusa,
a `SQLException` é apanhada, o log registra `"Error creating member list"` e a requisição
devolve `FAILURE`.

O curinga sozinho (`"*"`) fica na posição zero, o ramo não roda e a query sai válida — que é
por que o defeito nunca apareceu.

Não é alcançável pela aplicação: o `QueuedDatasetStore` só emite `OPEN`, `CLOSE` e os dois
`UPDATE`, e nunca `LIST` de membro. É um defeito latente, à espera de quem for usar a filtragem
por prefixo de membro.

**Nota:** `DatabaseTest.aWildcardInsideTheMemberNameBuildsInvalidSql` congela o comportamento
atual, com um controle positivo ao lado. Corrigir é trocar uma palavra, e o teste existe para
que isso seja uma decisão e não um efeito colateral de alguém mover o método.

---

## 12. Um subcomando telnet desconhecido derruba a sessão

**Arquivo:** [streams/TelnetListener.java](src/com/bytezone/dm3270/streams/TelnetListener.java)
— `processTelnetSubcommand`

```java
TelnetSubcommand subcommand = null;

if (data[2] == TelnetSubcommand.TERMINAL_TYPE)
  subcommand = new TerminalTypeSubcommand (...);
else if (data[2] == TelnetSubcommand.TN3270E)
  subcommand = new TN3270ExtendedSubcommand (...);
else
  logger.warn ("Unknown command type : {}", String.format ("%02X", data[2]));

addDataRecord (subcommand, SessionRecordType.TELNET);      // <- pode ser null
```

O `else` registra o aviso e **segue em frente com `subcommand` nulo**. Os dois caminhos de
`addDataRecord` estouram:

- em SPY ou REPLAY, onde `session != null`, o construtor do `SessionRecord` chama
  `message.size ()` sem checar — `NullPointerException`;
- em TERMINAL, `processMessage` chama `message.process (screen)` — `NullPointerException`
  também.

Ou seja: o aviso sugere que o subcomando desconhecido foi ignorado, e na linha seguinte a
sessão cai. Um host que negocie qualquer subopção telnet fora das duas conhecidas derruba a
conexão em vez de seguir sem ela.

A correção é um `return` depois do `logger.warn`, ou um `if (subcommand != null)` antes do
`addDataRecord` — mas isso muda comportamento observável, então fica aqui.

---

## 13. `SessionRow.getTime ()` devolve o nome do comando, não a hora

**Arquivo:** [application/SessionRow.java](src/com/bytezone/dm3270/application/SessionRow.java)

```java
public final String getTime ()
{
  return commandNameProperty ().get ();     // <- deveria ser timeProperty ()
}
```

O getter lê a *property errada*. Onde deveria devolver o `mm:ss` do registro, devolve o nome
do comando — `"Write"`, `"Read SF"` — e devolve `null` quando a mensagem não tem nome, ainda
que a hora esteja preenchida.

**Hoje é latente, e há duas razões para isso.** A coluna `mm:ss` do `SessionTable` se liga
pela string `"time"`, e o `PropertyValueFactory` resolve `timeProperty ()` antes de procurar
um getter — então a tabela nunca chega a este método. E nenhum arquivo de `src/` ou `test/` o
chama: foi verificado por *grep* antes de mover a classe.

**Por que ele foi movido em vez de corrigido.** O defeito nasceu dentro do `SessionRecord` e
veio junto quando a linha da tabela foi separada dele, no Passo 5. Corrigir seria um commit
`fix` nesta branch, que a Regra 1 proíbe. O que o passo fez foi impedir que ele *deixasse* de
ser latente: uma projeção que copiasse campo a campo pelos getters — que é o padrão de
`TableDatasets` — ativaria o defeito e poria o nome do comando na coluna da hora. Por isso o
`SessionRecord` expõe `getTimeText ()`, com nome diferente, e é dele que a linha copia.

`SessionRowTest` congela as duas faces do defeito, e é o teste que precisa ser invertido no
dia em que alguém corrigir isto.

---

## 14. Os registros de sessão são acrescentados fora da thread do JavaFX

**Arquivos:** [streams/TelnetListener.java](src/com/bytezone/dm3270/streams/TelnetListener.java)
— `processRecord`, [session/Session.java](src/com/bytezone/dm3270/session/Session.java) — `add`,
[application/SessionRows.java](src/com/bytezone/dm3270/application/SessionRows.java)

No modo Spy, `SpyServer` sobe duas threads de socket. Cada uma chega, pela cadeia
`TelnetSocket.listen` → `TelnetListener.processRecord`, a `session.add (sessionRecord)` — e
`add` avisa os ouvintes, que acrescentam uma linha à `ObservableList` que a `SessionTable`
está observando naquele instante. **Nada disso passa por `Platform.runLater`.**

Mutar uma coleção observável ligada ao grafo de cena a partir de outra thread não tem
garantia nenhuma no JavaFX: a `TableView` pode ler a lista no meio da alteração. Na prática
raramente se manifesta, porque a inserção é rápida e a tabela repinta por pulsação.

**O código sabe.** Os dois comentários estão lá desde antes desta refatoração:

```java
// add the SessionRecord to the Session - is it OK to do this from a non-EDT?   TelnetListener
sessionRecords.add (sessionRecord);       // should this be concurrent?         Session
```

E o `Platform.runLater` que existe logo abaixo, no mesmo método, protege **apenas** o
`processMessage` do modo Terminal — não o `add`.

**Não é o item 7.** Aquele é sobre os campos públicos mutáveis das requisições de banco
atravessando a fila do `DatabaseThread`; este é sobre a thread que escreve numa coleção do
JavaFX.

**O Passo 5 reproduziu isto de propósito.** A `Session` deixou de guardar a `ObservableList`,
que agora vive em `application.SessionRows`, mas o aviso continua saindo da thread do socket
e a inserção continua acontecendo nela. Envolver a notificação em `runLater` mudaria o
instante em que cada linha aparece na tabela, e o `SessionRecordListener` documenta a escolha.
A correção é decidir onde o `runLater` entra — provavelmente em `SessionRows.recordAdded`, que
é a única implementação e já está do lado da interface.

---

## 15. `SiteListStage` ignora o parâmetro `show3270e`, e a coluna aparece onde não devia

**Arquivo:** [application/SiteListStage.java](src/com/bytezone/dm3270/application/SiteListStage.java)

```java
public SiteListStage (Preferences prefs, String key, int max, boolean show3270e)
{
  ...
  fields.add (new PreferenceField ("Ext", 50, Type.BOOLEAN));    // sempre, sem olhar o flag
```

O quarto parâmetro do construtor **não é lido em lugar nenhum do corpo** — foi conferido por
*grep* em `src/`, `test/` e nos seis módulos de plugin: a palavra `show3270e` aparece uma
única vez no projeto inteiro, na própria assinatura.

**Não é só um parâmetro morto, e é por isso que está nesta lista.** Os dois únicos
chamadores passam valores *diferentes*, e de propósito:

```java
serverSitesListStage = new SiteListStage (prefs, "Server", 10, true);
clientSitesListStage = new SiteListStage (prefs, "Client", 6, false);
```

A intenção era não mostrar a coluna `Ext` — o TN3270E — na lista de **clientes**, e ela é
mostrada. Um site de cliente é o lado que o modo Spy escuta; não há negociação TN3270E a
declarar ali. O resultado é uma caixa de seleção visível, editável e **persistida** (a chave
`Client00Extended` é lida e gravada como as outras) num formulário onde ela não significa
nada.

**Por que não foi corrigido aqui.** Há duas correções possíveis e elas divergem no
comportamento: honrar o flag esconde uma coluna que hoje aparece — mudança visível na janela
Site Manager —, e remover o parâmetro assume que a coluna deve mesmo aparecer para os dois.
Escolher entre as duas é decisão de produto, não de refatoração, e a Regra 1 proíbe
qualquer das duas nesta branch.

Descoberto na medição que precedeu o Passo 7, e escapou da varredura de código morto do
Passo 10 porque um parâmetro sem uso não é um bloco `if (false)` nem um membro sem chamador
— ele *tem* chamador, e dois.

---

## 16. As setas do teclado numérico não movem o cursor, e logam um `WARN` a cada tecla

**Arquivo:** [application/ConsoleKeyPress.java:154-180](src/com/bytezone/dm3270/application/ConsoleKeyPress.java#L154-L180)

O bloco das setas é uma guarda `isArrowKey ()` com um `switch` de quatro casos e um `default`
que avisa o impossível:

```java
if (keyCodePressed.isArrowKey ())
  switch (keyCodePressed)
  {
    case LEFT:  ...  case RIGHT: ...  case UP: ...  case DOWN: ...

    default:
      logger.warn ("Impossible arrow key");
      break;
  }
```

**Não é impossível.** Medido com `javap` no `javafx-graphics-21.0.7`: `KeyCode.isArrowKey ()`
é `(mask & 4) != 0`, `UP` é construído com máscara **6** e `KP_UP` com máscara **70** — os dois
com o bit 4 ligado. Logo `KP_LEFT`, `KP_RIGHT`, `KP_UP` e `KP_DOWN`, que são as setas do
teclado numérico com **NumLock desligado**, passam pela guarda, erram os quatro `case` e caem
no `default`.

**Efeito hoje:** a seta do teclado numérico não move o cursor, o evento **não é consumido** —
segue para quem estiver ouvindo depois — e sai uma linha `WARN` por tecla. O `logback.xml` põe
`application` em INFO, então o aviso aparece.

**Por que a mensagem mente:** "Impossible" descreve o que o autor supôs, não o que acontece. O
aviso é a prova de que a suposição está errada, e nunca foi lido porque ninguém o associou ao
teclado numérico.

**Correção possível:** acrescentar as quatro constantes `KP_` aos `case` existentes, cada uma
para a mesma `Direction`. Muda comportamento nas três plataformas — a tecla passaria a mover o
cursor e a consumir o evento —, e por isso não foi feita nesta branch.

**A congelar** pelo `ConsoleKeyPressTest`, escrito adiante no Passo 8: ele afirma que `KP_LEFT`
produz o aviso, **não** move o cursor e **não** consome o evento.

---

## 17. Metade dos atalhos de teclado estava morta, e qual metade dependia da plataforma — **CORRIGIDO**

**Arquivo:** [application/ConsoleKeyPress.java:42-152](src/com/bytezone/dm3270/application/ConsoleKeyPress.java#L42-L152)

`KeyEvent.isShortcutDown ()` devolve `controlDown` no Windows e no Linux, e `metaDown` no
macOS — é o `com.sun.javafx.tk.Toolkit.getPlatformShortcutKey ()` que decide. A guarda de
copiar e colar roda **antes** da guarda de `isMetaDown ()` (86-130) e da de `isControlDown ()`
(139-152), e **retorna para toda tecla que não seja `C` ou `V`**:

```java
if (keyEvent.isShortcutDown ())
{
  if (keyCodePressed.isModifierKey ()) return;
  if (keyCodePressed == KeyCode.C) { ... return; }
  if (keyCodePressed == KeyCode.V) { ... return; }

  // For other shortcut combos, clear selection
  screen.getScreenSelection ().clearSelection ();
  return;                                          // <-- engole o resto da cadeia
}
```

**Efeito hoje, no macOS:** o bloco `isMetaDown ()` inteiro é inalcançável. `Cmd+ENTER`
(`newLine`), `Cmd+BACK_SPACE` e `Cmd+DELETE` (`eraseEOL`), `Cmd+H` (`home`), `Cmd+I`
(`toggleInsertMode`) e **`Cmd+F1`, `Cmd+F2`, `Cmd+F3` — que são as teclas PA1, PA2 e PA3 do
3270** — apenas limpam a seleção e somem.

**Efeito hoje, no Windows e no Linux:** `Ctrl+H` (`home`) é inalcançável pelo mesmo motivo.

**Que os dois foram escritos para funcionar** está nos comentários do próprio arquivo: a linha
101 diz `// OSX ctrl-h conflicts with Hide Windows command`, justificando o `Cmd+H`, e a 139
diz `// OSX has to share ctrl-h`, justificando o `Ctrl+H`. A guarda de copiar e colar, que o
comentário da linha 41 mostra ter sido acrescentada depois
(`// Handle copy/paste shortcuts before clearing selection`), engoliu a metade do macOS.

**CORRIGIDO no Passo 8**, excepcionalmente e a pedido do usuário — é a segunda dispensa da
Regra 1 nesta branch, e a primeira está no §4.13 do relatório. Este item fica na lista, e não
sai dela, porque a numeração é citada pelos comentários do `ConsoleKeyPressTest` e do próprio
`ConsoleKeyPress`.

A correção foi tirar o `return` do fim da guarda: `handleShortcut` passou a devolver `boolean`,
`C` e `V` continuam encerrando o tratamento, e todo o resto devolve `false` e volta a cair na
cadeia — que já limpava a seleção de toda tecla não-modificadora na linha seguinte. A limpeza
que estava dentro da guarda saiu junto, porque passou a ser feita duas vezes.

**O delta, medido caso a caso** — o que não está aqui não mudou:

| Combinação | Antes | Depois |
|---|---|---|
| `Cmd+ENTER` (macOS) | limpava a seleção | `newLine` + consome |
| `Cmd+BACK_SPACE` / `Cmd+DELETE` (macOS) | limpava a seleção | `eraseEOL` + consome |
| `Cmd+H` (macOS) | limpava a seleção | `home` + consome |
| `Cmd+I` (macOS) | limpava a seleção | `toggleInsertMode` + consome |
| `Cmd+F1` / `F2` / `F3` (macOS) | limpava a seleção | PA1 / PA2 / PA3 + consome |
| `Ctrl+H` (Windows, Linux) | limpava a seleção | `home` + consome |
| atalho+`LEFT` / `RIGHT`, teclado travado | limpava a seleção | `back` / `forward` + consome |
| atalho+`Shift+ENTER` | limpava a seleção | `newLine` + consome |
| `Ctrl/Cmd+C`, `+V`, modificador sozinho | — | inalterados |
| qualquer outro atalho | limpa, não consome | limpa, não consome |

**As duas últimas linhas de mudança são efeito colateral**, não intenção original: são
consequência de a cadeia voltar a rodar, e a guarda do teclado travado sempre foi a primeira
depois do atalho. Estão afirmadas no `ConsoleKeyPressTest`, com comentário dizendo isso.

**Validação manual pendente, e é honesto dizer:** o efeito mais visível desta correção é no
macOS, e ela foi feita numa máquina Windows. Aqui dá para exercitar `Ctrl+H` e
`Ctrl+LEFT`/`Ctrl+RIGHT` no modo histórico; as seis restaurações do macOS — inclusive PA1, PA2
e PA3 — não têm validação manual.

---

## Onde estão os defeitos que a refatoração *vai* resolver

Estes não estão nesta lista porque não são mudança de comportamento:

- **Colisão de FQCN entre JARs de plugin.** Os dois `com.bytezone.plugins.DocumentPage` com
  bytecode diferente colidem no `URLClassLoader` único, e a primeira definição encontrada
  vence para ambos os plugins — qual delas depende da ordem de listagem do diretório. Isso é
  indeterminismo, não comportamento a preservar. A correção (um class loader por JAR) faz
  cada plugin rodar deterministicamente o próprio código, que é o que o fonte de cada um já
  diz.
