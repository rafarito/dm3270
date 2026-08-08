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

---

## Metricas da suite

### Cobertura — JaCoCo

Sai junto com `mvn test`, sem comando extra:

```
dm3270           target/site/jacoco/index.html
dm3270-plugins   DownloadDataset/target/site/jacoco/index.html
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
dm3270-plugins   DownloadDataset/target/pit-reports/index.html
```

O PIT altera o bytecode (troca `>` por `>=`, inverte condicoes, remove chamadas) e
verifica se algum teste falha. Um mutante que **sobrevive** e uma linha que os testes
executam mas nao verificam de verdade — e o tipo de buraco que a cobertura sozinha nao
mostra.

`targetClasses` esta restrito aos pacotes que possuem testes. Apontar o PIT para a camada
JavaFX geraria milhares de mutantes sem cobertura e afogaria o sinal util.

### Como ler os dois numeros do PIT

| Metrica | O que significa |
|---|---|
| **Mutation coverage** | mutantes mortos / **todos** os mutantes. Cai junto com o codigo que nao tem teste nenhum. |
| **Test strength** | mutantes mortos / mutantes **cobertos**. Ignora o codigo sem teste e mede so a qualidade dos testes que existem. |

Num projeto com muita UI sem teste, o primeiro numero fica baixo por construcao; o segundo
e o que diz se os testes escritos valem alguma coisa.

### Situacao atual

| | `dm3270` | `dm3270-plugins` |
|---|---:|---:|
| Cobertura de instrucoes (projeto todo) | 14% | — |
| Cobertura das classes com teste | 53% | 53% |
| Mutantes gerados | 1676 | 215 |
| Mutation coverage | 49% | 42% |
| **Test strength** | **85%** | **74%** |

Os 14% do projeto todo refletem a camada JavaFX inteira sem teste, nao a qualidade da
suite. Nas classes efetivamente cobertas, `BufferAddress`, `ScreenDimensions` e os dois
`TextMaker` matam 100% dos mutantes; `TelnetProcessor` 88%; `StartFieldAttribute` 85%;
`Dm3270Utility` 78%.

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

| Pacote | Responsabilidade | Criticidade | Testavel isolado |
|---|---|:---:|:---:|
| `dm3270.utilities` | Conversao EBCDIC/ASCII, empacotamento de bytes, dump hex | **Critica** | sim |
| `dm3270.orders` | Orders do data stream (SBA, SF, SFE, RA, IC, PT, EUA, GE, FCO) e enderecamento de 12/14 bits | **Critica** | sim |
| `dm3270.attributes` | Atributo de inicio de campo e atributos estendidos (cor, destaque) | **Critica** | sim |
| `dm3270.commands` | Comandos 3270: Write, Erase Write, Read, EAU, WSF e a resposta AID | **Critica** | sim |
| `dm3270.buffers` | Encapsulamento telnet: escape de `0xFF`, terminador `IAC EOR` | **Critica** | sim |
| `dm3270.telnet` | Maquina de estados que separa o stream em registros, comandos e subcomandos | **Critica** | sim |
| `dm3270.extended` | TN3270E: cabecalho de comando, bind, respostas | Alta | sim |
| `dm3270.structuredfields` | Structured fields (Outbound3270DS, ReadPartition, SetReplyMode, EraseReset) | Alta | parcial |
| `dm3270.replyfield` | Query replies que declaram as capacidades do terminal | Alta | sim |
| `dm3270.filetransfer` | IND$FILE: parsing do comando, registros de transferencia | Alta | parcial |
| `dm3270.plugins` | API exposta a plugins externos (`PluginData`, `PluginField`, `ScreenLocation`) | Alta | sim |
| `dm3270.display` | Modelo da tela: `Screen`, `Field`, `FieldManager`, `Cursor`, `Pen`, `ScreenPacker` | **Critica** | **nao** |
| `dm3270.streams` | Sockets, SSL, negociacao (`TelnetState`, `TerminalServer`) | Alta | **nao** |
| `dm3270.session` | Gravacao e replay de sessoes | Media | parcial |
| `dm3270.database` | Cache SQLite de datasets e membros | Media | parcial |
| `dm3270.console` | Log de console | Baixa | nao |
| `dm3270.assistant` | Abas de datasets, jobs e transferencias (JavaFX) | Baixa | nao |
| `dm3270.application` | Janelas, teclado, ciclo de vida (JavaFX) | Baixa | nao |
| `reporter.record` | Divisao de um dataset em registros: FB, VB, RDW, LF, CR/LF | Alta | sim |
| `reporter.text` | Interpretacao dos bytes como EBCDIC ou ASCII, e deteccao do formato | Alta | sim |
| `reporter.file` | Pontuacao e escolha automatica do formato de um arquivo | Media | parcial |
| `reporter.reports` | Geracao de relatorios (texto, hex, ASA, natload) | Media | parcial |
| `reporter.application` | UI do visualizador (JavaFX) | Baixa | nao |

### `dm3270-plugins` — 12 arquivos, 5 plugins

| Modulo | Responsabilidade | Criticidade | Testavel isolado |
|---|---|:---:|:---:|
| `DownloadDataset` | `DocumentPage` le uma pagina de EDIT do ISPF; `Document` remonta o dataset a partir das paginas | **Critica** | sim |
| `ShowDataset` | Mesma logica do `DownloadDataset` mais uma janela de exibicao | **Critica** | sim |
| `ShowFields` | Inspeciona os campos da tela (ferramenta de depuracao) | Baixa | nao |
| `FanLogon` / `FanLogoff` | Automacao de logon/logoff no servidor publico FanDeZhi | Baixa | nao |

---

## Por que a camada de display ficou de fora

`Screen` estende `javafx.scene.canvas.Canvas`, e `Field`, `FieldManager`, `Cursor` e
`ScreenPacker` so existem a partir de uma instancia de `Screen`. Instanciar essas classes
exige o toolkit do JavaFX inicializado, o que um teste unitario comum nao faz.

Para cobrir esse modulo ha dois caminhos, em ordem de preferencia:

1. **Extrair o modelo da view.** `FieldManager`, `Cursor` e `ScreenPacker` operam sobre
   `ScreenPosition`, nao sobre pixels. Se dependessem de uma interface
   (`DisplayScreen` ja existe e quase serve) em vez da classe `Screen`, ficariam
   testaveis sem JavaFX. Esse e o refactor de maior retorno do projeto.
2. **TestFX + Monocle**, que sobem um toolkit headless. Resolve sem mexer no codigo,
   mas os testes ficam mais lentos e mais fragis.

O pacote `dm3270.streams` esta fora pelo mesmo motivo pratico: depende de sockets reais.
`TelnetState` isolado ja seria testavel e e um bom proximo alvo.

---

## Cobertura atual

| Area | Classe de teste | Testes |
|---|---|---:|
| Conversao e empacotamento | `Dm3270UtilityTest` | 25 |
| Enderecamento de buffer | `BufferAddressTest` | 26 |
| Orders do data stream | `OrderTest` | 41 |
| Atributos | `AttributeTest` + `StartFieldAttributeTest` | 47 |
| Encapsulamento telnet | `BufferTest` | 11 |
| Stream telnet | `TelnetProcessorTest` | 16 |
| Comandos 3270 | `CommandTest` + `WriteControlCharacterTest` | 34 |
| Resposta AID | `AIDCommandTest` | 35 |
| Cabecalho TN3270E | `CommandHeaderTest` | 22 |
| Query replies | `QueryReplyFieldTest` | 24 |
| IND$FILE | `IndFileCommandTest` | 27 |
| API de plugins | `PluginApiTest` | 27 |
| Dimensoes de tela | `ScreenDimensionsTest` | 7 |
| Registros (reporter) | `RecordMakerTest` | 22 |
| Texto EBCDIC/ASCII (reporter) | `TextMakerTest` | 23 |
| **`dm3270`** | | **387** |
| Pagina de EDIT (plugin) | `DocumentPageTest` | 34 |
| Montagem do documento (plugin) | `DocumentTest` | 10 |
| **`dm3270-plugins`** | | **44** |

O teste `TelnetProcessorTest.chunkingDoesNotChangeResult` reprocessa a mesma sessao com
todos os tamanhos de bloco possiveis (1 byte ate o stream inteiro) e exige resultado
identico — e a garantia de que a maquina de estados nao depende de como o socket fatia
os dados.

---

## Achados registrados nos testes

Casos em que o teste documenta o comportamento atual em vez de exigir o comportamento
esperado. Estao marcados no codigo com `REGRESSAO`, `LIMITACAO` ou `ATENCAO`.

| Onde | O que acontece |
|---|---|
| `IndFileCommand` | Os construtores `(TransferType, String, File)` e `(TransferType, String, byte[])` sempre lancam `NullPointerException`: `setCommandText()` chama `prefix.isEmpty()`, mas `prefix` so pode ser preenchido por `setPrefix()` — depois que o construtor ja rodou. Nenhum ponto do codigo de producao os usa, por isso a falha nunca apareceu. |
| `QueryReplyField.toString()` | Consulta a lista `replies`, preenchida apenas por `addReplyFields()` (modo replay). Imprimir uma reply recem-parseada fora desse fluxo lanca `NullPointerException`. |
| `CharacterSets` e `DistributedDataManagement` | Ambos avancam o ponteiro do laco com um tamanho lido do proprio buffer. Um buffer malformado com esse campo zerado prende o parser num **laco infinito**. Nao ha teste que dispare isso (travaria a suite); os testes usam apenas buffers bem formados. |
| `Dm3270Utility.toHex()` | Remove um unico caractere no fim para tirar a quebra de linha. No Windows o separador tem dois caracteres, entao sobra um `\r` no fim da saida. |
| `ScreenDimensions` | O construtor chama `BufferAddress.setScreenWidth()`, um estado estatico global. Duas telas de larguras diferentes nao coexistem sem que a formatacao de depuracao de uma vaze para a outra. |
| `DocumentPage` (plugin) | `getDatasetName()` sabe ler telas `BROWSE` e `VIEW`, mas `createPage()` so aceita a tela se algum campo casar com `EDIT_PATTERN`. Telas de BROWSE e VIEW sao sempre recusadas. |
| `DocumentPage` (plugin) | `getColumns()` localiza o cabecalho com `findFieldContaining("columns")`. Um cabecalho abreviado (`Col 1 72`) nunca e encontrado, e o ramo que compara `parts[i].equals("col")` fica inalcancavel. |
| `Document`/`DocumentPage` (plugin) | Os arquivos sao **identicos** em `DownloadDataset` e `ShowDataset` (byte a byte). Toda correcao precisa ser feita duas vezes. Candidatos naturais a um modulo comum. |

---

## Proximos alvos

1. `TelnetState` — negociacao TN3270E e estado da sessao, hoje sem cobertura.
2. `SessionRecord` / `SessionReader` — leitura dos arquivos de replay.
3. `reporter.file.ReportScore` — heuristica que escolhe o formato de um arquivo baixado.
4. `Field` / `FieldManager` / `Cursor`, depois do refactor descrito acima.
5. `structuredfields` — cobertura direta de `Outbound3270DS` e `ReadPartitionSF`.
