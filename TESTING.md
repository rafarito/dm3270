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

**Remedido no Passo 11, e portanto confiavel:** "Testes que exigem JavaFX" e a secao nova
"Mostrar uma janela num teste desliga o toolkit". A suite esta em **1.680** casos e **66**
classes, medidos com `mvn clean test`.

**Remedido no Passo 9, e ainda confiavel:** "Situacao atual", "Rede de seguranca", "JARs
sinteticos" e "Uma falha intermitente que nao e sua".

**COM UMA RESSALVA SOBRE O PIT, e ela importa:** o passo 9 NAO conseguiu rodar a passada de
mutacao completa - a maquina ficou sem memoria e a matou duas vezes. O que rodou foi escopado
as classes que o passo tocou, e esses numeros estao na "Situacao atual". **Os numeros GLOBAIS
de mutacao continuam sendo os do Passo 8** e estao marcados como tal; nao os cite como
remedidos.

**Remedido no Passo 8:** "Cobertura atual" e "Proximos alvos". Os numeros dessas secoes foram
medidos com `mvn clean test` e `mvn clean test-compile pitest:mutationCoverage`.

**Remedido no Passo 10, e ainda confiavel na ordem de grandeza:** "Cobertura por pacote —
`dm3270`", "Mapa de modulos" e "Uma falha intermitente que nao e sua". O unico pacote que mudou
desde entao e o `runtime`, que entrou no relatorio de mutacao no Passo 8 com **5 mutantes e 5
mortos (100%)** e nao aparece naquela tabela.

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

| | `dm3270` | `dm3270-plugins` |
|---|---:|---:|
| Testes | **1.640** (Passo 9) | 248 (63 no `UploadDataset`) |
| Cobertura de instrucoes (projeto todo) | 54% (Passo 8) | — |
| Cobertura de ramos | 50% (Passo 8) | — |
| Mutantes gerados | 4.024 (Passo 8) | — |
| Mutation coverage | 66% (2.647/4.024) (Passo 8) | — |
| **Test strength** | **86%** (Passo 8) | — |
| Classes no `targetClasses` | 165 (Passo 9) | — |

**O Passo 9 acrescentou 58 testes e duas classes ao `targetClasses`**, e a passada de mutacao
dele foi ESCOPADA, nao global - ver a ressalva na secao de frescor. Medido nas classes que o
passo tocou:

| Classe | Mutantes | Mortos | |
|---|---:|---:|---|
| `plugins.PluginJars` | 25 | 25 | **100%** |
| `plugins.PluginDigest` | 16 | 15 | 94% |
| `plugins.PluginData` | 45 | 33 | 73%, de 71% |

O `PluginData` subiu porque os **quatro metodos que ganhou entraram com 100%**; o que sobra
nele e de `listFields`, `toString`, `getField` e `trimField`, todos anteriores ao passo. O
unico sobrevivente das duas classes novas e `new StringBuilder (bytes.length * 2)` virando
divisao - a capacidade inicial do buffer, sem efeito observavel, **mutante equivalente**.

**Tres sobreviventes viraram teste, e nenhum deles sairia de releitura de codigo.** A tela de
apoio do `countModifiableFields` tinha dois campos protegidos e dois modificaveis, entao
contar os protegidos dava o mesmo numero; nenhum caso do `toHex` exercitava o digito 10, que e
onde a conversao decide entre somar a `'0'` e somar a `'A'` - e os dois MD5 usados, por acaso,
nao tem um unico `A`; e nenhum teste punha arquivo que nao fosse `.jar` na pasta de plugins.

**O terceiro precisou de duas tentativas, e essa e a parte reusavel.** A primeira versao punha
um `.txt` na pasta e afirmava que a descoberta achava um plugin so. Passava - **e o mutante
tambem passava**: com o filtro devolvendo sempre `true` o arquivo entra na lista, mas o
`new JarFile` falha nele com `IOException`, que a varredura loga e engole. O resultado era
identico. O que distingue e um arquivo que **e** um JAR valido, com um plugin dentro, mas que
nao termina em `.jar`: o codigo certo o ignora pelo nome, o mutante o abre. E a diferenca entre
exercitar o caminho e provar a decisao.

**O Passo 8 acrescentou 90 testes e NAO moveu nenhuma das duas coberturas, e isso tambem e
esperado.** Ele cobriu o `ConsoleKeyPress` (69 casos) e o novo `runtime.TerminalModel` (18), e
corrigiu um defeito (3). A cobertura de instrucoes e a de ramos ficaram em 54% e 50%: as ~250
linhas do tratador de teclado nao movem o arredondamento de um projeto com 59 mil instrucoes.
O PIT ganhou **cinco mutantes** — 4.019 para 4.024 —, todos do `TerminalModel`, e **todos os
cinco morreram**: o pacote `runtime` entra no relatorio com 100% de linha e 100% de mutacao.
A porcentagem global ficou em 66% e o test strength em 86%.

**`ConsoleKeyPress` nao produz mutante, e isso foi dito antes do primeiro commit do passo:**
`application` continua fora do `<targetClasses>`, pela mesma razao de sempre - e classe de
widget e entraria com centenas de sobreviventes. Um passo pode acrescentar 69 testes a uma
classe e nao mover o PIT em nada; reconhecer isso antes e o que impede de procurar um ganho
que nao existe.

**O `TerminalModel` entrou ACRESCENTANDO denominador**, pelo criterio ja usado com o `SiteValue`
no Passo 3 e o `ReportScore` no Passo 4: classe nova, sem JavaFX, com teste proprio. Quando a
classe entra com 100%, a porcentagem global pode ate subir uma fracao; quando entra com
sobreviventes, ela desce - e nos dois casos e a direcao honesta, porque o que muda e o que esta
sendo medido.

**O Passo 7 moveu a cobertura e NAO moveu o PIT, e as duas coisas sao esperadas.** Ele
acrescentou 40 testes sobre o `OptionStage`, que ate entao nao tinha nenhum: a cobertura de
instrucoes subiu de 51% para 54% e a de ramos de 48% para 50%. Os tres numeros do PIT ficaram
iguais - 4.019 mutantes, 66%, test strength 86% - porque **`application` nao esta no
`<targetClasses>`**, entao nem o `LaunchRequest` nem o `OptionStage` produzem mutante.
**Isso foi avaliado e mantido:** um `record` so geraria mutantes nos acessores gerados, quase
todos equivalentes, e o `OptionStage` e classe de widget - entraria com centenas de
sobreviventes e afogaria o sinal, pela mesma razao que deixa `SiteForm` e `Screen` de fora. O
`SessionRow` e o precedente: tem teste desde o Passo 5 e tambem nao esta na lista.

**Os numeros dos Passos 6 e 10 eram praticamente os mesmos entre si, e isso era esperado.** O
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

A refatoracao em curso preserva 100% do comportamento observavel. **Cinco** mecanismos
sustentam essa promessa, e todos rodam no `mvn test`:

| Mecanismo | Onde | O que protege |
|---|---|---|
| Golden master do parser | `ParserGoldenMasterTest` + `test/golden/mf-parse.txt` | Reprocessa uma sessao TN3270 real e congela tudo que o parser monta: registros, comandos, orders, respostas telnet. Cobre `telnet`, `buffers`, `commands`, `orders`, `extended`, `structuredfields` e `replyfield` de uma vez |
| Regras de camada | `LayeringTest` + `test/archunit-baseline/` | **Treze** regras de dependencia com ArchUnit. **Doze chegaram a zero e NAO sao congeladas** - uma violacao nova quebra a build sem baseline para absorve-la. So `uiIsTheOnlyPlaceThatKnowsJavaFx` segue congelada, em 240 violacoes, e o baseline versionado e o placar: ele so encolhe |
| Placar de ciclos | `LayeringTest.MAX_MUTUAL_CYCLES`, hoje **9** | Conta os pares de pacotes com dependencia mutua. Falha se subir **e** se cair sem atualizar o limite, para que todo ganho seja registrado no commit que o produziu |
| Caracterizacao | `SiteFormTest`, `OptionStageTest`, `ConsoleKeyPressTest`, `PluginsStageDispatchTest`, `ScreenContextPoolingTest`, `ReportScoreTest`, `SessionRecordTest`, `SessionTest`, e outros | Congela o comportamento atual das classes que serao desmontadas, **incluindo os defeitos** — ver [BACKLOG-DEFEITOS.md](BACKLOG-DEFEITOS.md) |
| Compatibilidade da API de plugins | `PluginApiShapeTest` + `LegacyPluginCompatibilityTest` | **Nasceu no Passo 9.** Afirma a forma BINARIA de `Plugin` e `DefaultPlugin` e carrega um JAR compilado no proprio teste. E o que torna a Regra 3 uma porta de build, e nao um ritual manual |

**A Regra 3 - "JARs de terceiros ja compilados tem de continuar carregando" - deixou de
depender de disciplina no Passo 9**, e vale entender por que sao DOIS testes e nao um.

Rodar `mvn install` aqui e `mvn test` no `../dm3270-plugins` prova compatibilidade de
**fonte**: la tudo e recompilado, e uma recompilacao absorve em silencio exatamente as
mudancas que quebram um JAR ja compilado. Transformar `DefaultPlugin.getModifiableFields` de
`protected static` em metodo de instancia deixa toda subclasse compilando igual e mata o
`invokestatic` gravado dentro do `DownloadDataset.jar` com `IncompatibleClassChangeError`; o
mesmo vale para transformar `DefaultPlugin` em interface, ou um `default` de `Plugin` em
metodo abstrato.

O `PluginApiShapeTest` cobre essa metade por reflexao - os seis metodos de `Plugin` e os sete
do `DefaultPlugin`, com **assinatura e modificador** de cada um, porque o modificador e parte
do contrato binario tanto quanto a assinatura. O `LegacyPluginCompatibilityTest` cobre a
outra: compila um plugin no meio do teste, empacota num JAR e o carrega pelo caminho de
verdade - varredura, descoberta, auto-registro, instanciacao e despacho.

**O teclado nao tem golden master, e o `ConsoleKeyPressTest` e o que existe no lugar.** Ele e a
unica rede sobre o despacho de tecla: 69 casos, um por binding, mais os dez caminhos que **nao**
consomem o evento. Dois deles congelam defeitos de proposito (itens 16 e 17 do backlog), e a
historia desse arquivo e o melhor argumento da branch a favor de escrever a rede ANTES do
refactor - **os dois defeitos so apareceram quando as asserticoes foram escritas**, e nenhuma
leitura de codigo os teria pegado.

**Como ele alcanca a classe, e por que isso importa para quem for escrever o proximo:** pelos
dubles das duas portas, nunca pelos campos. Os tres dubles escrevem na **mesma** lista, entao
cada caso afirma tambem a ordem *entre* colaboradores. Ele atravessou os tres commits de
refactor do Passo 8 sem uma linha mudada - e e exatamente isso que prova que aqueles commits
preservaram comportamento. Um teste que precisa ser ajustado pelo refactor que ele deveria
vigiar nao prova nada.

**Uma regra que chegou a zero e trocada pela regra nua**, com o baseline e a entrada dele no
`stored.rules` apagados. E isso que separa "hoje nao ha violacao" de "nao pode haver
violacao": um baseline pode ser refrozen com `-Darchunit.freeze.refreeze=true`, uma regra nua
nao pode ser afrouxada por comando nenhum.

Quando um golden master falhar, o teste grava o resultado obtido em `target/golden/` e
aponta a primeira linha divergente. Duas leituras possiveis: ou a refatoracao mudou
comportamento e deve ser revertida, ou a mudanca era pretendida e o snapshot precisa ser
reaprovado — o que exige justificativa no commit, nunca um `rm` silencioso.

### Testes que exigem JavaFX

`SiteForm`, `OptionStage` e `Screen` so podem ser instanciadas com o toolkit ativo, e **as
tres tem teste desde o Passo 11**. A extensao `JavaFxToolkit` liga o toolkit uma vez por JVM;
use com `@ExtendWith (JavaFxToolkit.class)`.

**Sao ONZE as classes de teste que usam a extensao desde o Passo 11** - eram sete. As quatro
novas sao a do construtor da `Screen` (`ScreenConstructionTest`) e as tres do caminho de
lancamento (`ConsoleStartTest`, `ConsoleLaunchErrorsTest`, `ConsoleShutdownTest`).

**E a `Screen` deixou de ser o caso impossivel.** Este arquivo dizia, e o `CLAUDE.md` tambem,
que ela "nao se instancia num teste hoje". Instancia, **sem costura nenhuma**: o construtor e
publico, e um teste fornece os oito argumentos - as duas `ScreenDimensions`, um no de
`Preferences` descartavel, o `TerminalFunction`, o `PluginsStage` pelo construtor de pacote
que o Passo 9 abriu, um `Site` que pode ser nulo, um `TelnetState` headless e um
`DatasetStore`. Nenhuma linha de `src/` mudou para o `ScreenConstructionTest` existir.

**O que construir uma `Screen` CUSTA, e nao esta em mais lugar nenhum:** ela toca o diretorio
pessoal de quem roda a suite. A cadeia e `Screen` -> `TransfersStage` -> `FilesTab` ->
`new ReporterNode (prefs)` -> `TreePanel.getTree (path)`, com
`Paths.get (System.getProperty ("user.home"), "dm3270", "files")` **hard-coded** em
`ReporterNode:61` - nao vem de preferencia, entao **um no descartavel nao o isola**. E o
`getTree` **cria** o diretorio se faltar e o percorre recursiva e avidamente. Numa maquina com
muitos arquivos baixados essa classe fica lenta, e o resultado passa a depender do disco.

**E a `ConsoleModelTest` NAO usa a extensao, o que e a assercao dela.** `Console` estende
`javafx.application.Application`, mas `new Console ()` nao toca o toolkit: os inicializadores
estaticos sao um `Logger`, um `int` e um `SiteValue` - classe sem um unico `import` desde o
Passo 3 -, e os de instancia sao `new ScreenDimensions (24, 80)` e `new TelnetState ()`. A
classe inteira roda em 1,4 s.

**E uma classe do Passo 9 deliberadamente NAO usa a extensao, o que e a assercao dela.** O
`PluginJarsTest` exercita o carregamento de JARs de plugin inteiro - pasta criada, descoberta,
os dois filtros que ela aplica, o loader de cada JAR, a classe ausente e a biblioteca solta -
**sem subir toolkit nenhum**. Enquanto aquilo morava dentro de uma `javafx.stage.Stage`, so
era testavel com o toolkit de pe; e a mesma natureza de assercao do `SiteValueTest` e do
`ReportScoreTest`, e esta dita no cabecalho da classe.

**A quarta da lista antiga nao e por instanciar widget nenhum:** o `ConsoleKeyPressTest` monta `KeyEvent` a mao, e montar um evento
realmente nao exige toolkit. O que exige e **le-lo**. `KeyEvent.isShortcutDown ()` chama
`com.sun.javafx.tk.Toolkit.getToolkit ()` para saber qual e o modificador de atalho da
plataforma - conferido com `javap` no `javafx-graphics-21.0.7` -, e ela e a **primeira** guarda
de `ConsoleKeyPress.handle`. Todo caso do arquivo passa por ela.

**E dai sai uma armadilha de portabilidade que vale para qualquer teste de teclado:**
`isShortcutDown ()` e `controlDown` no Windows e no Linux e `metaDown` no macOS. Um teste que
fixe um dos dois passa numa plataforma e quebra na outra. O `ConsoleKeyPressTest` mede o valor
do proprio toolkit, num helper `shortcutIsMeta ()`, e aperta a tecla certa em cada uma.

**A lista encolheu, e o encolhimento e o resultado da refatoracao.** `ScreenPosition` e `Pen`
sairam na Onda 1; `Site` virou uma porta com implementacao headless (`SiteValue`) no Passo 3;
`ReportScore` saiu no Passo 4; `Session` e `SessionRecord` sairam no Passo 5. Nos tres ultimos
casos **a ausencia da anotacao no teste e a assercao principal**, e esta dita no cabecalho de
cada classe de teste.

**O Passo 7 acrescentou uma classe a lista, e nao ha como evitar:** o `OptionStageTest`
constroi uma `Stage`, e o construtor de `Stage` exige a thread do JavaFX -
`JavaFxToolkit.onFxThread (...)` existe para isso. Duas coisas dele valem copiar:

- ele **nao le os campos da classe sob teste**. Acha as linhas do formulario pelo texto do
  `Label`, os radios pelo `userData` e o menu pela `MenuBar`, tudo pelo grafo de cena. Por
  isso atravessou intacto o commit que fechou os dez campos do `OptionStage` - uma rede presa
  aos campos teria de ser reescrita justo no commit que ela deveria vigiar;
- ele **isola as `Preferences`**. Cada caso recebe um no proprio sob `userRoot`, com nome
  aleatorio, removido no `@AfterEach`. O `OptionStage` le seis chaves ja no construtor e
  grava as mesmas seis; sem o isolamento a suite escreveria nas preferencias reais de quem a
  roda. **E o primeiro teste do projeto a fazer isso** - se voce precisar de `Preferences`
  num teste novo, copie daqui.

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

### Mostrar uma janela num teste desliga o toolkit

**Esta e a armadilha mais cara do Passo 11, e o sintoma nao aponta para a causa.**

O `implicitExit` do JavaFX e `true` por default: quando a **ultima janela e fechada**, o
runtime se desliga sozinho. E o toolkit **nao religa na mesma JVM**. Logo o primeiro teste que
mostrar e fechar uma janela mata todos os seguintes - e eles falham em

```
o trecho na thread do JavaFX nao terminou em 30s
```

num `@BeforeEach` que nao tem nada de errado, porque o `Platform.runLater` deles entra numa
fila que ninguem mais atende. Medido numa classe de seis casos: **um passa, cinco estouram em
60 s cada** - 30 no `@BeforeEach` mais 30 no `@AfterEach`. Total: 306 s contra 7,3 depois da
correcao.

A correcao e uma linha, e esta no proprio `JavaFxToolkit`:

```java
Platform.setImplicitExit (false);
```

**E isso explica, retroativamente, uma disciplina que o projeto seguia sem registrar o
motivo:** nenhum teste de `Stage` desta suite jamais chamou `show ()`. O `OptionStageTest` e o
`PluginsStageDispatchTest` tem **zero** chamadas, e o cabecalho dos dois justifica isso por
"alcance pelo grafo de cena" - que e verdade, mas nao e a razao. A razao e esta, e ate o Passo
11 ninguem precisou descobri-la porque ninguem mostrou janela.

**E `javafx.stage.Stage.show ()` e `final`** - conferido com `javap` no
`javafx-graphics-21.0.7`. `Window.hide ()` nao e. Ou seja: um duble **nao consegue** suprimir
a exibicao, so observa-la. Quem precisar disso tem duas saidas, e as duas estao em uso:

- observar pela `showingProperty ()`, que dispara de forma **sincrona** dentro do proprio
  `show ()` - entao a ordem numa lista compartilhada continua sendo a ordem real das chamadas;
- **fechar a janela no `@AfterEach`**, obrigatoriamente, senao a suite acumula janelas abertas.

`Console.start (Stage)` termina em `optionStage.show ()`, entao as tres classes do caminho de
lancamento mostram janela de verdade enquanto rodam. Num ambiente headless, `xvfb-run`.

### JARs sinteticos: compilar um plugin dentro do teste

`test/com/bytezone/dm3270/testing/SyntheticPluginJar.java`, do Passo 9, escreve fontes Java,
compila com `ToolProvider.getSystemJavaCompiler ()` e empacota num JAR dentro de um `@TempDir`.
E o que permite carregar um plugin pelo caminho de verdade sem depender de nenhum arquivo
commitado no repositorio.

```java
new SyntheticPluginJar ()
    .add ("com.example.legacy.LegacyPlugin", "package com.example.legacy; ...")
    .writeTo (pluginsDirectory, "LegacyPlugin.jar");
```

`writeTo` tem uma sobrecarga que empacota **so as classes nomeadas**, embora todas sejam
compiladas juntas. Serve para montar um JAR de plugin que depende de uma classe que mora
noutro JAR da mesma pasta - o javac precisa das duas juntas, e o empacotamento precisa delas
separadas.

**TRES ARMADILHAS, e as tres custam tempo:**

- **o classpath de compilacao NAO pode vir de `System.getProperty ("java.class.path")`.** O
  Surefire roda com `useManifestOnlyJar` ligado por padrao, e aquela propriedade contem apenas
  o *booter jar*: o `javac` nao enxerga nem `Plugin` nem `DefaultPlugin`, e a mensagem de erro
  e um `cannot find symbol` sem explicacao. O classpath e montado a partir do
  `getProtectionDomain ().getCodeSource ().getLocation ()` de quatro classes ancora -
  `target/classes`, `target/test-classes`, o slf4j (porque `DefaultPlugin` guarda um `Logger`)
  e o `javafx.base` (porque `PluginField` importa `javafx.beans.property`, e a API de plugins
  ja nasceu com JavaFX dentro);

- **`getSystemJavaCompiler ()` devolve `null` sobre um JRE**, e o harness **falha alto** em vez
  de virar um `assumeTrue`. Pular a rede que prova a Regra 3 porque a maquina esta mal
  configurada e exatamente o que a Regra 5 proibe;

- **todo teste que abre um JAR tem de fechar os class loaders no `@AfterEach`.** Ver a secao
  seguinte.

### Uma falha intermitente que nao e sua

**Qualquer classe de teste que use `@TempDir` pode falhar sem que nada esteja errado no
codigo.** Sao dez desde o Passo 9: `DatabaseTest`, `TransferManagerTest`, `TransferTest`,
`SessionReaderTest`, `SessionTest`, `ReportTesterTest`, `PluginsStageDispatchTest`,
`LegacyPluginCompatibilityTest`, `PluginClassLoadingTest` e `PluginJarsTest`. A mensagem:

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
Tests run: 1492, Failures: 0, Errors: 1
```

ou seja, **zero falhas de assercao**: o teste passou e a infraestrutura tropecou depois dele.

Foi observada duas vezes durante o Passo 10, **em classes diferentes** - primeiro em
`ReportTesterTest.describesFileOnDisk`, depois em `SessionReaderTest` -, e nao reproduziu em
nenhuma das outras seis execucoes da suite no mesmo dia. A primeira versao desta secao culpava
o `ReportTesterTest`; a segunda ocorrencia mostrou que a classe e circunstancial e o que importa
e o `@TempDir`.

**Antes de investigar uma mudanca sua, rode de novo.** Se reproduzir com consistencia, ou sempre
na mesma classe, ai sim ha o que investigar.

**E ha uma causa DETERMINISTICA da mesma mensagem, que nao e intermitente e nao adianta rodar
de novo:** um `URLClassLoader` aberto sobre um JAR dentro do `@TempDir`. No Windows o arquivo
fica mapeado enquanto o loader existir, e a exclusao falha sempre. As quatro classes de teste
do Passo 9 que constroem JARs fecham os loaders no `@AfterEach` - `stage.closeClassLoader ()`
ou `jars.close ()` - e e por isso que elas nao aparecem nessa falha. **Quem escrever a
proxima tem de fazer o mesmo**; se a mensagem aparecer sempre, e este o motivo, nao o do
paragrafo anterior.

### PIT interrompido deixa forks orfaos

Descoberto no Passo 9, e vale para qualquer execucao cancelada: **`pitest:mutationCoverage`
nao limpa os proprios forks quando e morto no meio.** Seis deles ficaram segurando 1,4 GB numa
maquina de 7 GB, e as execucoes seguintes morriam por falta de memoria - o sintoma parece
lentidao da suite ou do Maven, e nao tem nada a ver com codigo.

```powershell
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
  Where-Object { $_.CommandLine -match 'pitest' }
```

Se a lista nao estiver vazia e nao houver PIT rodando, mate os processos antes de culpar a
proxima execucao. E, numa maquina apertada, `-Dthreads=1` e um escopo menor de
`-DtargetClasses` resolvem o problema na origem.

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
| `dm3270.display` | 12% | 14% | 50% | so `FxPalette` esta no PIT; a `Screen` (**1.109** linhas) ganhou o `ScreenConstructionTest` no Passo 11, mas continua fora do PIT por ser widget |
| `dm3270.plugins` | 20% | 23% | 60% | **instantaneo do Passo 10.** Desde o Passo 9 entraram `PluginDigest` e `PluginJars` no PIT, e o `PluginsStage` - hoje **665** linhas - continua fora, por ser widget |
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

**Total do projeto: 54% de instrucoes, 50% de ramos** (Passo 8; a tabela acima e um
instantaneo do Passo 10 e os percentuais por pacote nao foram regenerados desde entao). O
numero reflete a camada JavaFX sem
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

### `dm3270` — 294 arquivos, 27 pacotes, 34.504 linhas

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
| `dm3270.plugins` | 13 | 2.067 | API de plugins. Desde o Passo 9: tres papeis (`Activatable`, `AutoPlugin`, `RequestPlugin`), o `PluginDigest` e o `PluginJars` (headless, com teste proprio). `PluginsStage` caiu de 892 para **665** linhas e continua JavaFX | **sim** |
| `dm3270.display` | 13 | 2.794 | **So a view**: `Screen` (**1.109**), `FieldManager`, `ScreenPacker`, fontes | nao |
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

### `dm3270-plugins` — 15 arquivos, **6** plugins

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

**E os cortes continuaram depois daquela onda, sempre pelo mesmo padrao: uma porta estreita no
lugar da classe concreta.** As duas mais recentes sao do Passo 8, e valem como modelo porque
mostram a regra que decide ONDE a porta mora:

| Porta | Mora em | Implementada por | Por que ali |
|---|---|---|---|
| `application.ConsoleKeyTarget` | `application` | `ConsolePane` | consumidor **e** implementador estao no mesmo pacote — `ConsolePane` e de `application`, nao de `display`. E o caso mais simples, sem risco de camada |
| `screen.KeyboardTarget` | `screen` | `display.Screen` | **nao pode** morar em `application`: quem implementa e `display.Screen`, e `displayDoesNotDependOnApplication` vale integralmente, em zero |

**A regra geral, que o `CLAUDE.md` enuncia e que estas duas ilustram:** a porta e declarada no
pacote que CONSOME — exceto quando o implementador nao puder depender desse pacote, e ai ela
desce para um pacote que os dois possam ver. `screen` e esse pacote na maioria dos casos, e e
por isso que `AidSender` e `KeyboardState` moram la.

**Uma consequencia de desenho que aparece nas duas:** a porta expoe a ACAO, nao o objeto.
`KeyboardTarget` declara `clearSelection ()` e nao `getScreenSelection ()`, porque
`ScreenSelection` e de `display`, importa JavaFX e guarda uma `Screen` — devolve-la poria a view
dentro do modelo de tela e quebraria `screenModelDoesNotDependOnTheView`. Os tres sitios que a
chamavam faziam todos a mesma coisa.

O que ainda falta, e por que:

- **`Screen`** continua com **1.109** linhas e oito responsabilidades - mas **deixou de nao
  ter teste no Passo 11**: o `ScreenConstructionTest` congela o construtor, que e o motivo
  real para mexer nela. **Decompo-la deixou de ser prioridade na Onda 3**, que mediu e
  descobriu que os ciclos vinham dos `import` dela, nao do tamanho - seis cairam sem quebrar
  uma classe sequer. O que a rede nova congela e a ORDEM dentro do construtor, e em especial
  que a tela **nao e desenhada durante a construcao** - `eraseScreen ()` e `draw ()` ficam
  atras de `if (screenPositions != null)`, e o vetor so nasce depois da chamada reentrante do
  `FontManager`. Reordenar essas linhas muda comportamento observavel.
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

### `dm3270` — 66 classes de teste, 1.680 testes

**A TABELA ABAIXO E UM INSTANTANEO DO PASSO 8 e lista 54 classes, nao 66.** Faltam as sete que
o Passo 9 acrescentou, todas em `test/com/bytezone/dm3270/plugins/`: `PluginsStageDispatchTest`
(20 casos), `PluginJarsTest` (8), `LegacyPluginCompatibilityTest` (3), `PluginClassLoadingTest`
(2), `PluginApiShapeTest` (9), `DefaultPluginTest` (10) e `PluginDigestTest` (4). O total de
1.640 esta certo; a tabela e que nao foi regenerada.

**Remedida no Passo 8**, contando elementos `<testcase>` nos relatorios do Surefire, que e a
unica contagem que fecha (ver "Ler o resultado da suite", no `RELATORIO-REFATORACAO.md` §5.18):

```bash
grep -ho "<testcase" target/surefire-reports/*.xml | wc -l          # 1.680
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
| `dm3270.application.ConsoleKeyPressTest` | 69 |
| `dm3270.application.OptionStageTest` | 38 |
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
| `dm3270.runtime.TerminalModelTest` | 18 |
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
| Upload de dataset | `UploadDatasetTest`, `UploadContextTest`, `NoFixedDelayTest` | 63 |
| **Total** | | **227 anotacoes, 248 casos no Surefire** |

**O `UploadDataset` faltava nesta tabela**, e ele e justamente o modulo com a melhor cobertura
do repositorio - e o unico alvo de refatoracao que sobrou la. Acrescentado no Passo 9.

`DocumentPageTest`, `DocumentTest` e `ScreenBuilder` aparecem duas vezes porque
`Document.java` e `DocumentPage.java` existem em duas copias, em `DownloadDataset` e
`ShowDataset`. A duplicacao dos testes espelha a duplicacao do codigo.

**ESTE PARAGRAFO DIZIA DUAS COISAS ERRADAS, e as duas foram corrigidas no Passo 9.**

Ele dizia que as copias sao "identicas byte a byte". **Nao sao**, e a do `Document` diverge
SEMANTICAMENTE: o `stitch ()` do `DownloadDataset` remonta o arquivo fielmente e reinicia o
contador de linha entre faixas horizontais; o do `ShowDataset` prefixa o numero da linha no
texto e usa `leftColumn + 6` onde o outro usa `leftColumn - 1`. Os dois `DocumentPage`
divergem em robustez: um casa os marcadores ISPF em qualquer caixa, o outro nao (item 2 do
`BACKLOG-DEFEITOS.md`).

E ele mandava apagar as copias "ao extrair as duas classes para um modulo comum".
**NAO EXTRAIA MODULO COMUM.** E decisao explicita do usuario, registrada como Regra 4 no
`CLAUDE.md` deste repositorio e como Regra A no do `dm3270-plugins`: cada plugin e
independente e implementa a mesma ideia de formas diferentes. O diagnostico original leu a
divergencia como copia defasada e estava errado.

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
- **`Document` e `DocumentPage` duplicados nos dois plugins — e a duplicacao e PROPOSITAL.**
  Esta entrada dizia que os arquivos sao "identicos byte a byte" e que extrair um modulo
  comum "e o refactor certo". **As duas afirmacoes estao erradas**, e foram corrigidas no
  Passo 9.

  As copias **divergem**, e a do `Document` diverge semanticamente - uma remonta o arquivo
  fielmente, a outra prefixa numeros de linha e desloca as colunas. E **nao se extrai modulo
  comum**: e decisao explicita do usuario (Regra 4 no `CLAUDE.md`, Regra A no do repositorio
  de plugins), porque cada plugin e independente e implementa a mesma ideia de formas
  diferentes.

  **O que o Passo 9 fez foi o oposto de unificar: deu a cada JAR o proprio class loader.**
  Ate entao os dois JARs dividiam um loader unico e as duas copias colidiam - a primeira
  encontrada valia para os dois plugins, com o desempate decidido pela ordem de listagem do
  diretorio. Hoje cada plugin roda deterministicamente a propria copia, que e o que o fonte
  de cada um sempre disse.

---

## Proximos alvos

**Os SEIS alvos desta lista foram feitos** - os quatro originais mais dois que ela nem
registrava, e essa e a parte que importa: a maior lacuna de teste do projeto (o caminho de
lancamento inteiro, com zero casos) nao estava escrita aqui. Ficam riscados, porque a ordem em
que cairam e o argumento de que o metodo funciona:

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

5. ~~**`OptionStage` e o caminho de lancamento.**~~ Feito no Passo 7, e ele nao estava nesta
   lista - o caminho inteiro (`Console`, `OptionStage`, `ConsoleKeyPress`) tinha **zero**
   testes, o que e a maior lacuna que esta secao deixou de registrar. Hoje o `OptionStage`
   tem 38.
7. ~~**O `PluginsStage` e a API de plugins.**~~ Feito no Passo 9, e tambem nao estava nesta
   lista: a classe tinha **750 linhas e zero testes**, e estava fora do `targetClasses` do
   PIT. Hoje tem 32, e a rede achou **quatro defeitos** - os itens 18 a 21 do
   [BACKLOG-DEFEITOS.md](BACKLOG-DEFEITOS.md) -, dois deles impossiveis de ver lendo o
   codigo. O carregamento de JARs saiu da janela para o `PluginJars`, que roda headless e
   entrou no PIT com **100% de mutacao**.

6. ~~**`ConsoleKeyPress` e o `setModel`.**~~ Feito no Passo 8, e rendeu mais do que testes: a
   medicao achou **dois defeitos novos** que nenhum documento registrava, e que so apareceram
   porque a rede foi escrita antes do refactor. Sao os itens 16 e 17 do
   [BACKLOG-DEFEITOS.md](BACKLOG-DEFEITOS.md). O `ConsoleKeyPress` tem 69 casos, e o
   `Console.setModel` virou `runtime.TerminalModel`, com 18. **O `Console` continua sem teste
   proprio** - o que sobra nele e construcao, e isso e o composition root.

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
3. **O caminho de lancamento, o que sobrou dele**: **so o `Console`**. O `OptionStage` saiu
   desta linha no Passo 7, com 38 casos, e o `ConsoleKeyPress` saiu no Passo 8, com 69. A Onda
   4 do diagnostico fechou.

   O que sobra no `Console` nao e despacho, e **construcao** - `Screen`, `ConsolePane`,
   `SpyPane`, `MainframeStage` e `ReplayStage` dentro dos ramos de um `switch`. Isso e o
   composition root, que e o item 1 desta lista, e nao se testa sem desmontar a fiacao.

   **A armadilha das tres teclas continua valendo, e agora tem teste:** sem modificador,
   `BACK_SPACE` chama `backspace ()` e `DELETE` chama `delete ()` - so `END` chama
   `eraseEOL ()`. Tratar as tres como a mesma acao trocaria "apagar um caractere" por "apagar
   ate o fim da linha". O `ConsoleKeyPressTest` tem um caso para cada uma, e as quatro acoes
   que aparecem em mais de um binding tem um caso que **conta** quantas combinacoes as
   alcancam - e onde um `Map` que fundisse bindings apareceria como numero errado.

   **A receita que funcionou, para quem for fazer o mesmo com outra classe presa a widget:**
   estreitar os colaboradores PRIMEIRO, escrever a rede DEPOIS, decompor por ultimo. As duas
   portas do Passo 8 cobriram os onze metodos que o `ConsoleKeyPress` usava, e so entao a rede
   ficou barata - antes delas ela exigiria uma `Screen` de 1.109 linhas e um `ConsolePane` de
   442, nenhum dos dois instanciavel num teste.

   **O `Console` deixou de ser intestavel no Passo 11**, e por menos do que se supunha: `new
   Console ()` nao toca o toolkit, e `startSelectedFunction` - embora privado - ja e injetado
   como `Runnable` em `optionStage.setOnConnect (this::startSelectedFunction)`. Quem tem o
   `OptionStage` tem o gatilho. Com tres metodos de pacote (as duas fabricas de `Stage` e o
   alerta) a rede alcanca o `start`, os quatro ramos que RECUSAM o lancamento e o `stop`.

   O que continua fora de alcance sao os caminhos FELIZES do `switch`: eles constroem
   `Screen`, abrem arquivo SQLite no diretorio corrente, mostram janela e abrem socket. Isso
   e o composition root, o item 1 desta lista - e o que falta agora e tirar a construcao de
   dentro dos ramos, nao dar rede ao `Console`.
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
