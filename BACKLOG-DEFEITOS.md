# Backlog de defeitos

Defeitos reais encontrados durante a refatoração estrutural e **deliberadamente não
corrigidos** nela.

A refatoração tem uma regra: preservar 100% do comportamento observável. Corrigir qualquer
item desta lista mudaria o resultado de alguma operação, então cada um foi mantido como
está — inclusive quando o código novo teve de reproduzir o defeito de propósito. Isso separa
duas decisões que não deveriam se misturar: *reorganizar* e *mudar o que o programa faz*.

Cada item abaixo é uma decisão pendente, não uma tarefa aprovada. Corrigir é uma escolha do
time, num commit `fix(...)` próprio, com teste que falha antes e passa depois.

**Como um item sai desta lista, e quem decide.** Já aconteceu três vezes, e nas três a
autorização foi do usuário, pedida explicitamente antes de tocar no código:

1. **medir** e registrar o item aqui, com o efeito de hoje e o delta que a correção causaria;
2. **escrever a rede que congela o comportamento atual** — inclusive o defeito;
3. **perguntar ao usuário.** Se a resposta for não, o item fica e a rede o mantém congelado;
4. se for sim, um commit `fix` próprio, com o delta completo no corpo e a menção de que a
   Regra 1 foi dispensada a pedido dele.

O item corrigido **continua nesta lista**, marcado, e não é renumerado: os comentários do código
e dos testes citam o número. Hoje estão nessa situação os **itens 17 e 22**. O **item 23** também continua na lista, marcado como **não é defeito**: a correção foi autorizada, e a medição anterior ao `fix` mostrou que o comportamento está certo.

**Os três commits `fix` da branch são `cfc95f18`, `da0e89a8` e o do item 22, no Passo 12** —
são os únicos pontos em que um `git bisect` procurando mudança de comportamento pode parar.

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

**Congelado desde o Passo 8** pelo `TerminalModelTest` (18 casos, e um deles existe só para
dizer que o modelo 5 **é** válido) e pelo `setModel` reescrito, onde a condição que reproduz o
defeito está escrita e comentada em vez de escondida num `break` que falta:

```java
if (terminalModel.isEmpty () || model == 5)         // o case 5 sem break, item 1 do backlog
  logger.warn ("Invalid model number: {}", model);
```

As duas armadilhas continuam de pé: o modelo 5 configura 27x132 **e então** reclama, e o caminho
inválido **não** atribui `alternateScreenDimensions` — que é campo de instância reaproveitado
entre lançamentos na mesma JVM, então um modelo inválido herda o valor do lançamento anterior.
Um `TerminalModel` que devolvesse sempre um valor mudaria isso, e é por isso que `forNumber`
devolve `Optional`.

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

**Arquivo:** [reporter/reports/DefaultReportMaker.java:51-73](src/com/bytezone/reporter/reports/DefaultReportMaker.java#L51-L73)
(desde o Passo 4 o primeiro parâmetro é `ReportContext`, não `ReportScore`)

```java
@Override
public String getFormattedRecord (ReportContext context, Record record)
{
  return "Not possible";        // string mágica no lugar de dado
}

// e o mesmo na sobrecarga (context, record, offset, length)

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

**Não é só hipotético, e isto foi medido em 2026-09-27:** `AsaReport` e `NatloadReport`
sobrescrevem a sobrecarga de dois argumentos, mas **não** a de quatro, e o
`ReportScore.getSubrecord` chama a de quatro em dois casos — quando a página começa ou termina
no meio de um registro (o `AsaReport` parte registros quando `allowSplitRecords`) e **sempre
que a página tem um registro só** (`firstRecord == lastRecord`). Nesses casos os dois
relatórios devem mostrar `"Not possible"` no lugar do conteúdo. Ainda não há caso que o prove:
o ciclo C8 do `PLANO-SOLID-2.md` escreve esse caso antes de tornar os métodos `abstract`, e
preserva o literal nas duas subclasses.

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

**A corrida que este item descrevia não existe mais, e isto foi medido em 2026-09-27.** O
texto original dizia que a thread da UI lia esses campos depois de `processResult ()`. Depois
da Onda 3 e do Passo 2 isso não acontece: o `DatabaseThread` chama
`request.initiator.processResult (request)` **na própria thread do worker**, o
`QueuedDatasetStore` transforma o resultado em `listener.storeCompleted (request.toString ())`
ainda nessa thread, e os únicos ouvintes (`FieldManager`) só fazem `logger.debug` com a
`String`. Nenhum campo é lido de outra thread depois da construção. Os testes leem os campos
depois de esperar num `CountDownLatch`, que estabelece o *happens-before*.

**O que sobra é encapsulamento, não defeito:** os campos continuam públicos e mutáveis, e
qualquer código novo que os lesse fora do worker reabriria a corrida. Fechá-los é o ciclo C9 do
`PLANO-SOLID-2.md`, como `refactor`. O item fica aqui até lá, para não se perder.

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
os **sete testes** da classe aninhada `Cache` do `DatabaseTest` (99 linhas), encolhendo a suíte
em sete casos e o denominador do PIT em `database.*` (a conta foi medida no Passo 10, quando a
suíte tinha 1.452; hoje tem **1.582**, e a subtração é a mesma). Perder denominador é a
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

**Congelado por** `ConsoleKeyPressTest`, escrito no Passo 8: quatro casos parametrizados —
`KP_LEFT`, `KP_RIGHT`, `KP_UP` e `KP_DOWN` — afirmam que a tecla **não** move o cursor e
**não** consome o evento. O aviso no log é o terceiro efeito e não está afirmado ali; se alguém
quiser congelá-lo também, precisa de um *appender* de teste no logback.

**Como isto foi achado**, porque o método vale mais que o item: `javap -c` na própria JavaFX.
Nenhuma leitura do `ConsoleKeyPress` revelaria — a guarda parece exaustiva, o `default` parece
defensivo, e o nome da mensagem afirma que é impossível.

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

## 18. Um `requestMenuItem` nulo entra no menu, e todo rebuild seguinte estoura

**Arquivo:** [plugins/PluginsStage.java](src/com/bytezone/dm3270/plugins/PluginsStage.java)

`doesRequest ()` é perguntado em **dois** sítios durante a montagem do menu, e eles não
compartilham o resultado:

```java
// PluginEntry.select (), :474 - so cria o item se ainda nao existir
if (requestMenuItem == null && plugin.doesRequest ())
  requestMenuItem = new MenuItem (name.getText ());
...
// setMenu (), :265 - decide se o item entra no menu, e NAO confere se ele existe
if (pluginEntry.isAutoActivate () && pluginEntry.plugin != null
    && pluginEntry.plugin.doesRequest ())
  menu.getItems ().add (pluginEntry.requestMenuItem);
```

Se o plugin responder `false` na primeira pergunta e `true` na segunda, o item nunca é criado
e o `:265` acrescenta **`null`** à lista de itens do menu. A `Menu` do JavaFX estoura
`NullPointerException` dentro do próprio *listener* de mudança da lista — `Cannot invoke
MenuItem.getParentMenu()` —, e isso acontece **na thread da aplicação, sem propagar** para
quem chamou: a janela continua de pé, com um `null` guardado na lista.

**E o defeito tem um segundo sintoma, pior que o primeiro.** O `null` fica lá, e o
`rebuildMenu ()` — que roda a cada clique num item de plugin — tenta removê-lo:

```java
while (items.size () > baseMenuSize)
  items.remove (menu.getItems ().size () - 1);        // :286
```

`Cannot invoke MenuItem.setParentMenu(...)`. **Toda troca de plugin passa a estourar**, para
sempre, até a aplicação ser reiniciada.

**Hoje é latente, e a razão é estreita.** Entre as duas perguntas só roda o laço sobre os
outros plugins, e nenhum dos seis mexe no `doesRequest` alheio — então as duas respostas são
sempre iguais. Mas `doesRequest` **muda durante a execução** em cinco dos seis (10 transições
vivas), e o guarda que faltava — conferir `requestMenuItem != null` no `:265`, como o
`rebuildMenu ()` já faz no `:290` — não existe.

**Correção sugerida:** acrescentar `pluginEntry.requestMenuItem != null` à condição do `:265`.
Uma linha.

**Por que não foi corrigido:** Regra 1. O `PluginsStageDispatchTest` congela a trava em volta
disso; o caso que disparava o `NullPointerException` foi deliberadamente reescrito para não
disparar, porque um teste que suja a saída da suíte a cada execução é pior do que um item de
backlog com o repro escrito. O repro é: registrar um plugin ativo cujo `doesRequest ()`
devolva `false` na primeira chamada e `true` na segunda, e chamar `getMenu ()`.

---

## 19. `processAll` captura `Exception`, não `Throwable` — um `Error` cancela os plugins seguintes

**Arquivo:** [plugins/PluginsStage.java](src/com/bytezone/dm3270/plugins/PluginsStage.java)

```java
for (PluginEntry pluginEntry : plugins)
  if (pluginEntry.isActivated)
  {
    Plugin plugin = pluginEntry.plugin;
    if (plugin != null && plugin.doesAuto ())
      try { plugin.processAuto (data); }
      catch (Exception e) { logger.error ("Error processing auto", e); }
  }
```

O `try/catch` existe justamente para que um plugin quebrado não derrube os outros — e cumpre
isso para `Exception`. Mas um `Error` — `StackOverflowError` numa recursão do plugin,
`NoClassDefFoundError` numa classe que falta no JAR dele, `AssertionError` de um `assert` do
próprio plugin — **escapa do laço**, cancela os plugins que ainda não rodaram e sobe até
[commands/WriteCommand.java:120](src/com/bytezone/dm3270/commands/WriteCommand.java#L120),
que logo depois de destravar o teclado avisa a tela (`hostWriteCompleted`), e a tela chama
`processPluginAuto ()` pela `screen.HostWriteCompletion`. Desde o ciclo C2 o `Error` atravessa
esses dois degraus a mais, sem que nenhum o capture.

`NoClassDefFoundError` é o caso realista: é exatamente o que um JAR de plugin incompleto
produz, e o subsistema de plugins carrega classes de JARs de terceiros por reflexão.

**O efeito visível** é que os plugins registrados *depois* do que quebrou param de funcionar
sem nenhuma mensagem que os nomeie — a diferença entre "o plugin X falhou" e "os plugins
pararam".

**Correção sugerida:** capturar `Throwable`, ou pelo menos `Exception | LinkageError`, e
nomear o plugin na mensagem — hoje o log diz `"Error processing auto"` sem dizer qual.

**Por que não foi corrigido:** Regra 1. O caso `umErrorEscapaEAbortaOsSeguintes` do
`PluginsStageDispatchTest` congela o comportamento atual, e é o teste que precisa ser
invertido no dia em que alguém corrigir isto.

---

## 20. `processPluginRequest` não tem guarda de `doesRequest ()` nem `try/catch`

**Arquivo:** [plugins/PluginsStage.java](src/com/bytezone/dm3270/plugins/PluginsStage.java)

O caminho automático e o de *request* são assimétricos nos dois sentidos, e nenhum documento
registrava isso:

| | `processAll` (auto) | `processPluginRequest` |
|---|---|---|
| confere se o plugin quer ser chamado | `doesAuto ()`, a cada tela | **não confere nada** |
| isola a falha | `try/catch (Exception)` + log | **nenhum** |

Quem aciona é o item de menu, e a decisão de esse item existir foi tomada lá atrás, na trava
do item 18 — possivelmente **muitas telas antes**. Entre uma coisa e outra o plugin pode ter
zerado o próprio `doesRequest`, que é o que o `FanLogoff` faz na linha 115 e o
`DownloadDataset` faz no `abort ()`. O plugin é chamado assim mesmo.

E como não há `try/catch`, uma exceção sobe pelo `setOnAction` do item de menu até o
tratador de exceções não capturadas da thread do JavaFX — depois de o `processReply` já ter
possivelmente escrito campos na tela.

**Correção sugerida:** as duas metades são independentes. A guarda é uma linha; o `try/catch`
deveria ser o mesmo do `processAll`, para que os dois caminhos falhem do mesmo jeito.

**Por que não foi corrigido:** Regra 1. Os casos `processRequestRodaSemGuarda` e
`excecaoNoRequestSobe` do `PluginsStageDispatchTest` congelam os dois.

---

## 21. `getMenu ()` re-instancia todo plugin, e chama `activate ()` outra vez

**Arquivo:** [plugins/PluginsStage.java](src/com/bytezone/dm3270/plugins/PluginsStage.java)

`getMenu ()` chama `setMenu ()`, que chama `pluginEntry.instantiate ()` — e `instantiate ()`
começa com `plugin = null` e constrói um objeto novo por reflexão. Numa segunda chamada de
`getMenu ()`:

- o plugin antigo é **descartado com todo o seu estado**, sem que `deactivate ()` seja
  chamado nele;
- `activate ()` roda no objeto novo, que começa do zero;
- o `requestMenuItem`, que é campo do `PluginEntry` e não do plugin, **sobrevive** — e por
  isso a pergunta `doesRequest ()` do `:474` não se repete, saindo pelo curto-circuito da
  trava.

Para o `FanLogon` isso significaria perder `fanDeZhi`, `offset`, usuário e senha no meio de um
logon; para o `UploadDataset`, perder o `UploadContext` e o estado da máquina no meio de um
envio.

**Hoje é latente porque `getMenu ()` é chamado uma vez só**, em
[application/ConsolePane.java:107](src/com/bytezone/dm3270/application/ConsolePane.java#L107),
na montagem da barra de menus. Nada o chama de novo.

**Correção sugerida:** `instantiate ()` devolver o plugin existente quando já houver um, ou
`setMenu ()` não re-instanciar o que já está montado.

**Por que não foi corrigido:** Regra 1, e o fato de ser inalcançável hoje. O caso
`getMenuDeNovoReinstanciaEAtivaOutraVez` do `PluginsStageDispatchTest` congela a sequência.

---

## 22. `Console.stop ()` guardava seis colaboradores contra nulo, e não guardava o sétimo — **CORRIGIDO**

**Arquivo:** [application/Console.java](src/com/bytezone/dm3270/application/Console.java)

O `stop ()` é defensivo de forma quase sistemática — `mainframeStage`, `spyPane`,
`consolePane`, `replayStage`, os dois `WindowSaver` e a `screen` eram todos testados contra
nulo antes de serem usados. **O `optionStage` não era**: `savePreferences ()` o desreferenciava
direto, na primeira linha, e um `Console` que nunca chegou ao fim do `start (Stage)` estourava
`NullPointerException` ao ser parado.

A assimetria é o que chamava atenção: sete campos na mesma situação, seis protegidos e um não.

**Não era teórico.** `Application.stop ()` é chamado pelo runtime do JavaFX no fechamento, e
`start (Stage)` declara `throws Exception`. Qualquer falha antes de o `OptionStage` nascer
deixa o objeto exatamente nesse estado — e o candidato mais realista é o `PluginsStage`, que é
construído na linha imediatamente anterior e que varre a pasta de plugins, monta um class
loader por JAR e grava preferências.

### O delta, por inteiro

```java
  private void savePreferences ()
  {
-   optionStage.savePreferences ();
+   if (optionStage != null)
+     optionStage.savePreferences ();

    if (screen != null)
    { ... }
  }
```

| | Antes | Depois |
|---|---|---|
| `stop ()` num `Console` que completou o `start` | grava as preferências e fecha o class loader | **inalterado** |
| `stop ()` num `Console` que nunca chegou ao `start` | `NullPointerException` escapando de `Application.stop ()` | não grava preferência nenhuma, fecha o que houver, e **retorna normalmente** |

Nada mais muda. A guarda do `screen`, que já existia, cobre o `prefs` junto: os dois só nascem
depois do `init ()`, e sem tela não há fonte a gravar.

**Autorizado pelo usuário**, caso a caso, no Passo 12 — é o **terceiro** commit `fix` desta
branch, depois de `cfc95f18` (avisar quando a conexão falha) e `da0e89a8` (o atalho que
engolia a cadeia de teclas). O caso `stoppingAConsoleThatNeverStartedIsSafe` do
`ConsoleShutdownTest` afirma o comportamento novo; até a correção ele se chamava
`stoppingAConsoleThatNeverStartedThrows` e afirmava o oposto, e o comentário dele registra as
duas pontas.

---

## 23. O construtor da `Screen` dimensiona o canvas com uma geometria e a reporta com outra — **NÃO É DEFEITO**, reclassificado no Passo 13

**Arquivo:** [display/Screen.java](src/com/bytezone/dm3270/display/Screen.java)

Duas regras diferentes de "qual é a dimensão corrente" convivem dentro do **mesmo**
construtor:

| Quem pergunta | Qual regra | Com `alternate = 27×132` |
|---|---|---|
| `fontChanged`, que dimensiona o `Canvas` | `alternate ?? default` | **132 colunas** |
| `getScreenDimensions ()` | o `currentScreen`, fixado em `DEFAULT` | **80 colunas** |

O construtor escolhe `alternate ?? default` para dimensionar cursor, campos, histórico e o
vetor de `ScreenPosition`, e o `fontChanged` disparado pelo `FontManager` usa a mesma regra
para calcular a largura do canvas. Mas a última coisa que o construtor faz antes de entregar
o objeto é `setCurrentScreen (ScreenOption.DEFAULT)` — e `getScreenDimensions ()` responde
pelo `currentScreen`, não pelo campo.

O resultado é uma tela recém-construída cujo canvas tem largura de 132 colunas e que
responde "80" a quem lhe pergunta o tamanho.

**Hoje é latente** porque o host manda um Erase Write Alternate antes de usar a geometria
alternativa, e aí `setCurrentScreen (ALTERNATE)` alinha as duas respostas. O desalinhamento
existe só na janela entre a construção e o primeiro comando.

**Correção sugerida:** decidir a geometria corrente **uma vez**, e fazer o dimensionamento do
canvas e o acessor lerem a mesma decisão.

**Por que não foi corrigido:** Regra 1, e porque mexer nisso é mexer na ordem do construtor —
que é exatamente o alvo do composition root. O caso
`theCanvasIsSizedByTheAlternateWhileTheAccessorReportsTheDefault` do `ScreenConstructionTest`
congela as duas respostas.

**RECLASSIFICADO NO PASSO 13: não é defeito, e não foi corrigido.** A correção estava
autorizada, e a medição feita antes do `fix` mostrou que as duas respostas estão certas, cada
uma pelo próprio contrato:

- **o canvas usa a maior geometria de propósito.** O comentário está em `Screen.fontChanged`:
  `// always use the largest available screen`. Assim a janela não muda de tamanho quando o
  host alterna entre Erase Write e Erase Write Alternate;
- **o acessor reporta a partição corrente, e ela começa na padrão.** Pelo protocolo 3270, o
  terminal começa na partição de tamanho padrão e só um Erase Write Alternate
  (`WriteCommand:99`) o leva à alternativa. O `setCurrentScreen (DEFAULT)` do fim do construtor
  redimensiona cursor, pen, histórico, campos e mensagem para a partição em que o host vai
  escrever primeiro. Isso é o comportamento correto, e não uma "reversão silenciosa".

As duas correções propostas eram regressões. A **(a)**, em que o acessor passa a concordar com o
canvas, faria a tela nascer na partição alternativa: um `Write` sem erase antes do primeiro EW
endereçaria com 132 colunas. A **(b)**, em que o canvas passa a concordar com o acessor, faria a
janela crescer a cada EWA, justamente o que o comentário do `fontChanged` evita. **O usuário
decidiu não corrigir.**

O que a medição achou de lado: a largura global `BufferAddress.setScreenWidth` só é lida no
`BufferAddress.toString` (a coluna de comandos do replay e o log). Não afeta endereçamento.

O caso de caracterização continua, com o comentário corrigido. Agora ele congela a divisão
entre os dois contratos, e não mais um "defeito".

---

## 24. Um arquivo que não é uma sessão gravada abre um replay vazio, em silêncio

**Arquivos:** [session/SessionReader.java](src/com/bytezone/dm3270/session/SessionReader.java),
[application/LaunchCoordinator.java](src/com/bytezone/dm3270/application/LaunchCoordinator.java)

O ramo do replay valida **uma** coisa sobre o arquivo escolhido: que ele existe
(`Files.exists`). Dali em diante não há validação de formato em ponto nenhum.

`SessionReader.readFile` captura **apenas** `IOException`, e nesse caso devolve uma lista
vazia em vez de propagar. O laço de *parse* que vem depois não reclama de linha que não sirva:
ele simplesmente não encontra nenhuma. O resultado é uma `Session` **vazia**, cujo
`getServerName ()` responde `"Unknown"` e cujo `getScreenDimensions ()` responde `null`.

O `LaunchCoordinator` lança essa sessão vazia como lançaria qualquer outra: pede a tela, não
acha o site chamado `"Unknown"`, monta o console sem site e **abre a janela de replay**. Sem
dados, sem aviso, sem entrada no log. Para o usuário, a aplicação aceitou o arquivo e mostrou
uma sessão em branco.

**Medido**, com um arquivo de uma linha contendo texto qualquer:

| | |
|---|---|
| Alertas | **nenhum** |
| Operações do lançamento | `hideOptions`, `launchRequest`, `useAlternateScreenDimensions`, `findServerSite`, `showConsole`, `showReplay` — o caminho feliz inteiro |
| Nome de servidor procurado | `"Unknown"` |
| Sessão entregue à janela | `Empty session` |

**Por que é latente na prática:** o combo de arquivos do `OptionStage` é preenchido por
`getSessionFiles`, que lista a pasta de spy, então o usuário normalmente só escolhe arquivos
que a própria aplicação gravou. O caminho aparece quando a pasta tem outro arquivo, quando uma
gravação foi truncada, ou quando alguém aponta a pasta de spy para um diretório qualquer pelo
botão `Folder...`.

**Correção sugerida:** o `SessionLoader` recusar uma sessão sem nenhum registro, ou o
`LaunchCoordinator` tratar uma sessão vazia como o erro que ela é — a mensagem
`"Error creating replay window"` já existe e já reabre a janela de opções.

**Por que não foi corrigido:** Regra 1. O caso `aFileThatIsNotARecordedSession` do
`LaunchCoordinatorPathsTest` congela o comportamento atual, inclusive a ausência de alerta.

**Como foi achado:** pela rede do Passo 12, no primeiro `mvn test`. O caso tinha sido escrito
afirmando o contrário — que um arquivo inválido cairia no `catch` do ramo do replay e viraria
`"Error creating replay window"` — e falhou. É a quinta vez seguida que a rede escrita antes
do refactor desmente o plano.

---

## 25. `UploadDataset`: o aborto no meio da rolagem horizontal estoura `NullPointerException` logo depois

**Arquivo:** `../dm3270-plugins/UploadDataset/src/com/bytezone/plugins/UploadDataset.java`,
método `fillEmptyLines`

Quando um bloco tem linhas mais longas que o campo de conteúdo, o plugin digita `RIGHT n` e
depois `LEFT MAX` no campo de comando. Se o campo de comando não é encontrado na tela, ele
chama `abort (...)`, e o aborto funciona: aviso `ERROR` ao usuário, estado `IDLE`, `doesAuto`
desligado, **`context = null`**.

Só que o `abort` não encerra o método. A última instrução de `fillEmptyLines` é um
`logger.debug (...)` cujos argumentos incluem `context.getLinesSent ()` e
`context.getTotalLines ()`, e **argumento de método é avaliado mesmo com o nível DEBUG
desligado**. O resultado é um `NullPointerException` com a mensagem `Cannot invoke
"com.bytezone.plugins.UploadContext.getLinesSent()" because "this.context" is null`.

O host (`PluginsStage.dispatchAuto`) captura `Exception` e loga `Error processing auto` com a
pilha inteira. **Para o usuário o efeito é pequeno:** o aborto já aconteceu, e o aviso já tinha
saído. O que sobra é um erro espúrio no log, com uma pilha que aponta para o lugar errado e
esconde o motivo real, que é o aviso de logo antes.

São dois ramos, e os dois têm o mesmo defeito: o do `RIGHT` (bloco que não cabe na tela) e o do
`LEFT MAX` (fim de um bloco que já tinha sido rolado).

**Por que é latente na prática:** o campo de comando do EDIT não costuma sumir entre uma tela e
a seguinte. Aparece quando o ISPF troca de painel no meio do upload, ou quando uma mensagem
longa sobrepõe a linha de comando.

**Correção sugerida:** `return` logo depois dos dois `abort (...)` de `fillEmptyLines`, ou o log
de depuração ler o contexto só quando ele não é nulo.

**Por que não foi corrigido:** Regra 1. O `ScrollAbortTest` do módulo `UploadDataset` congela
os dois ramos como estão, inclusive a mensagem exata do NPE, para que a decomposição do
`UploadDataset` não o corrija de carona nem o troque por um NPE de outra linha.

**Como foi achado:** lendo o método antes de extraí-lo, na decomposição do `UploadDataset`
(Passo 14). Nenhum dos 63 casos existentes do módulo passava por esse caminho.

---

## 26. `UploadDataset`: uma segunda requisição recusada derruba o upload em andamento com uma mensagem enganosa

**Arquivo:** `../dm3270-plugins/UploadDataset/src/com/bytezone/plugins/UploadDataset.java`,
método `processRequest`

O item de menu do plugin continua disponível durante um upload. Se o usuário o aciona de novo e
confirma o diálogo, `processRequest` faz `context = result.get ()` **antes** de validar o
arquivo, sobrescrevendo o contexto do upload em andamento. Se a validação recusa o arquivo novo
(erro de LRECL, arquivo ilegível, tela sem campo de comando), cada recusa faz `context = null`,
e **nenhuma** delas mexe em `state` nem em `doesAuto`.

O resultado é um upload antigo com `doesAuto` ligado e sem contexto. Na tela seguinte o
`processAuto` o encerra com `Estado interno inconsistente: nenhum upload em andamento.`, uma
mensagem que não diz ao usuário que foi a segunda requisição que interrompeu a primeira. Se o
modo era REPLACE, o aviso ainda omite que o `DELETE ALL` já tinha sido executado, porque o
aborto só acrescenta esse aviso quando há contexto.

Cancelar o diálogo da segunda requisição não causa nada: o `return` vem antes da atribuição.

**Correção sugerida:** validar numa variável local e só trocar o contexto quando o novo upload
de fato começa, ou recusar a requisição enquanto `doesAuto ()` estiver ligado.

**Por que não foi corrigido:** Regra 1. Os casos `DuringAnUpload` do
`UploadDatasetRequestTest` congelam os dois caminhos: o cancelamento, que preserva tudo, e a
recusa, que zera só o contexto.

**Como foi achado:** medindo o `processRequest` antes de tirar dele a máquina de estados, na
decomposição do `UploadDataset` (Passo 14). A extração natural, validar numa variável local,
teria corrigido o defeito de carona.

---

## Onde estão os defeitos que a refatoração *vai* resolver

Estes não estão nesta lista porque não são mudança de comportamento:

- **Colisão de FQCN entre JARs de plugin.** Os dois `com.bytezone.plugins.DocumentPage` com
  bytecode diferente colidem no `URLClassLoader` único, e a primeira definição encontrada
  vence para ambos os plugins — qual delas depende da ordem de listagem do diretório. Isso é
  indeterminismo, não comportamento a preservar. A correção (um class loader por JAR) faz
  cada plugin rodar deterministicamente o próprio código, que é o que o fonte de cada um já
  diz.
