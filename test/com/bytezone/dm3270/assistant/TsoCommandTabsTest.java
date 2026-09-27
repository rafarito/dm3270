package com.bytezone.dm3270.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.datasets.DatasetStore;
import com.bytezone.dm3270.datasets.DatasetSummary;
import com.bytezone.dm3270.display.HeadlessScreenTarget;
import com.bytezone.dm3270.orders.BufferAddress;
import com.bytezone.dm3270.screen.AidSender;
import com.bytezone.dm3270.screen.Field;
import com.bytezone.dm3270.screen.KeyboardState;
import com.bytezone.dm3270.screen.KeyboardStatusChangedEvent;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.testing.JavaFxToolkit;
import com.bytezone.dm3270.utilities.Dm3270Utility;
import com.bytezone.dm3270.watch.ScreenWatcher;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.HBox;

/*
 * Caracterizacao do TSOCommand e do que as abas do assistant fazem com ele.
 *
 * O pacote assistant nao tinha teste nenhum, e as cinco abas escreviam direto no campo de
 * texto e no botao Execute do TSOCommand - onze acessos a dois campos de pacote. E isso que o
 * ciclo C7 vai fechar, e esta e a rede que falta antes.
 *
 * O TESTE ALCANCA O CAMPO E O BOTAO PELO GRAFO DE CENA (a HBox do TSOCommand), E NAO PELOS
 * CAMPOS - a mesma receita do OptionStageTest, e pela mesma razao: os dois campos viram
 * private no fim do ciclo, e um teste preso a eles teria de ser reescrito no commit que ele
 * protege. As abas sao alcancadas pelo conteudo (getContent) e pelos metodos que o resto da
 * aplicacao ja chama nelas; nenhum show ().
 *
 * O que fica congelado:
 *
 *   - a regra de habilitacao do Execute: desabilitado se o teclado esta travado, se nao ha
 *     ScreenWatcher, se a tela nao tem campo de comando TSO, ou se o texto esta vazio;
 *   - que o evento de teclado so recalcula o botao na aba selecionada;
 *   - apagar o comando: texto vazio E botao desabilitado, sem consultar a regra;
 *   - o texto que cada aba monta - Commands, Batch Jobs, Datasets e Transfers;
 *   - o Execute: poe o texto no campo TSO e manda ENTER; sem texto ou sem tela, nada.
 *
 * A aba Local Files fica de fora: o ReporterNode dela varre ~/dm3270/files, e o no de arquivo
 * que dispara o texto so existe com arquivo de verdade em disco. O acesso dela e o mesmo
 * setText das outras.
 *
 * O ScreenWatcher e um dublê que so fixa as tres respostas que as abas leem - o campo de
 * comando, se a tela e a do TSO e o prefixo. O campo em si e de verdade: vem de uma tela
 * minima com "Command ===>" processada pelo HeadlessScreenTarget, porque o Execute escreve
 * nele.
 */
// -----------------------------------------------------------------------------------//
@ExtendWith (JavaFxToolkit.class)
@DisplayName ("TSOCommand - o comando que as abas do assistant montam")
class TsoCommandTabsTest
// -----------------------------------------------------------------------------------//
{
  private static final byte ERASE_WRITE = 0x05;
  private static final byte SBA = 0x11;
  private static final byte SF = 0x1D;
  private static final byte WCC_RESET_KEYBOARD = (byte) 0xC2;
  private static final int PROTECTED = 0x20;
  private static final int UNPROTECTED = 0x00;

  private final Keyboard keyboard = new Keyboard ();
  private final RecordingAidSender aidSender = new RecordingAidSender ();
  private TSOCommand tsoCommand;
  private Field commandField;

  // ---------------------------------------------------------------------------------//
  @BeforeEach
  void setUp ()
  // ---------------------------------------------------------------------------------//
  {
    commandField = commandField ();
    tsoCommand = onFx ( () -> new TSOCommand ());
    tsoCommand.setConsolePane (aidSender);
  }

  // ---------------------------------------------------------------------------------//
  //  Dublês
  // ---------------------------------------------------------------------------------//

  private static class Keyboard implements KeyboardState
  {
    boolean locked;

    @Override
    public boolean isKeyboardLocked ()
    {
      return locked;
    }
  }

  private static class RecordingAidSender implements AidSender
  {
    final List<String> sent = new ArrayList<> ();

    @Override
    public void sendAID (byte aid, String name)
    {
      sent.add (String.format ("%02X %s", aid, name));
    }

    @Override
    public void sendAID (AIDCommand command)
    {
      sent.add (command.toString ());
    }
  }

  private static class StubWatcher extends ScreenWatcher
  {
    private final Field tsoCommandField;
    private final boolean tsoCommandScreen;
    private final String prefix;
    private final List<DatasetSummary> datasets = new ArrayList<> ();

    StubWatcher (Field tsoCommandField, boolean tsoCommandScreen, String prefix)
    {
      super (null, new ScreenDimensions (24, 80), DatasetStore.NONE);
      this.tsoCommandField = tsoCommandField;
      this.tsoCommandScreen = tsoCommandScreen;
      this.prefix = prefix;
    }

    @Override
    public Field getTSOCommandField ()
    {
      return tsoCommandField;
    }

    @Override
    public boolean isTSOCommandScreen ()
    {
      return tsoCommandScreen;
    }

    @Override
    public String getPrefix ()
    {
      return prefix;
    }

    @Override
    public List<DatasetSummary> getDatasets ()
    {
      return datasets;
    }

    @Override
    public List<DatasetSummary> getMembers ()
    {
      return new ArrayList<> ();
    }
  }

  // uma tela que o ScreenWatcher de verdade reconhece como tendo linha de comando: o
  // rotulo com 12 posicoes na coluna 1 da linha 1 e o campo de entrada de 66 depois dele
  // ---------------------------------------------------------------------------------//
  private static Field commandField ()
  // ---------------------------------------------------------------------------------//
  {
    List<Integer> stream = new ArrayList<> ();
    stream.add ((int) ERASE_WRITE);
    stream.add ((int) WCC_RESET_KEYBOARD);
    field (stream, 0, 0, PROTECTED, "  Menu  Help");
    field (stream, 1, 0, PROTECTED, "Command ===>");
    field (stream, 1, 13, UNPROTECTED, "");
    field (stream, 2, 0, PROTECTED, "title");

    byte[] buffer = new byte[stream.size ()];
    for (int i = 0; i < buffer.length; i++)
      buffer[i] = (byte) (int) stream.get (i);

    HeadlessScreenTarget screen = new HeadlessScreenTarget ();
    Command.getCommand (buffer, 0, buffer.length).process (screen);

    Field field = screen.getScreenWatcher ().getTSOCommandField ();
    assertNotNull (field, "a tela minima deixou de ter campo de comando");
    return field;
  }

  // ---------------------------------------------------------------------------------//
  private static void field (List<Integer> stream, int row, int col, int attribute,
      String text)
  // ---------------------------------------------------------------------------------//
  {
    int position = row * 80 + col;
    stream.add ((int) SBA);
    stream.add (BufferAddress.address[(position >> 6) & 0x3F] & 0xFF);
    stream.add (BufferAddress.address[position & 0x3F] & 0xFF);
    stream.add ((int) SF);
    stream.add (attribute);
    for (char ch : text.toCharArray ())
      stream.add (Dm3270Utility.asc2ebc[ch]);
  }

  // ---------------------------------------------------------------------------------//
  //  Utilitarios
  // ---------------------------------------------------------------------------------//

  private static <T> T onFx (JavaFxToolkit.FxSupplier<T> supplier)
  {
    return JavaFxToolkit.onFxThread (supplier);
  }

  private static void onFx (Runnable action)
  {
    JavaFxToolkit.onFxThread ( () ->
    {
      action.run ();
      return null;
    });
  }

  private ScreenWatcher ordinaryScreen ()
  {
    return new StubWatcher (commandField, false, "");
  }

  private <T extends Node> T find (Parent parent, Class<T> type)
  {
    for (Node node : parent.getChildrenUnmodifiable ())
      if (type.isInstance (node))
        return type.cast (node);
    throw new AssertionError ("sem " + type.getSimpleName () + " em " + parent);
  }

  private TextField commandText ()
  {
    return find (tsoCommand.getBox (), TextField.class);
  }

  private Button executeButton ()
  {
    return find (tsoCommand.getBox (), Button.class);
  }

  private String text ()
  {
    return onFx ( () -> commandText ().getText ());
  }

  private boolean executeDisabled ()
  {
    return onFx ( () -> executeButton ().isDisable ());
  }

  // a aba vai para uma TabPane depois de uma aba qualquer, que e a que a TabPane seleciona
  // sozinha; select (tab) e o que torna isSelected () verdadeiro
  private void select (Tab tab)
  {
    onFx ( () ->
    {
      TabPane tabPane = new TabPane (new Tab ("outra"), tab);
      tabPane.getSelectionModel ().select (tab);
    });
    assertTrue (tab.isSelected ());
  }

  private void keyboardChanged (AbstractTransferTab tab)
  {
    onFx ( () -> tab.keyboardStatusChanged (
        new KeyboardStatusChangedEvent (false, keyboard.locked, "")));
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("o proprio TSOCommand")
  class TheCommandBox
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("rotulo, campo e botao, nessa ordem; o campo comeca vazio, o botao ligado")
    void layout ()
    {
      onFx ( () ->
      {
        List<Node> children = tsoCommand.getBox ().getChildren ();
        assertEquals (3, children.size ());
        assertEquals ("TSO Command", ((Label) children.get (0)).getText ());
        assertTrue (children.get (1) instanceof TextField);
        assertEquals ("Execute", ((Button) children.get (2)).getText ());
      });
      assertEquals ("", text ());
      assertFalse (executeDisabled ());
    }

    @Test
    @DisplayName ("Execute poe o texto no campo TSO e manda ENTER")
    void executeSendsTheCommand ()
    {
      tsoCommand.screenChanged (ordinaryScreen ());
      onFx ( () ->
      {
        commandText ().setText ("TSO LISTC");
        executeButton ().fire ();
      });

      assertEquals ("TSO LISTC", commandField.getText ().trim ());
      assertEquals (List.of (String.format ("%02X ENTR", AIDCommand.AID_ENTER)),
                    aidSender.sent);
    }

    @Test
    @DisplayName ("Execute com o texto vazio nao manda nada")
    void executeWithoutText ()
    {
      tsoCommand.screenChanged (ordinaryScreen ());
      onFx ( () -> executeButton ().fire ());

      assertTrue (aidSender.sent.isEmpty ());
    }

    @Test
    @DisplayName ("Execute antes de haver tela nao manda nada")
    void executeWithoutScreen ()
    {
      onFx ( () ->
      {
        commandText ().setText ("TSO LISTC");
        executeButton ().fire ();
      });

      assertTrue (aidSender.sent.isEmpty ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("a regra do Execute, pela aba Commands")
  class TheExecuteRule
  // ---------------------------------------------------------------------------------//
  {
    private CommandsTab tab;

    @BeforeEach
    void createTab ()
    {
      tab = onFx ( () -> new CommandsTab (keyboard, tsoCommand));
    }

    @SuppressWarnings ("unchecked")
    private ListView<String> commandList ()
    {
      return (ListView<String>) tab.getContent ();
    }

    private void chooseCommand (ScreenWatcher watcher, String command)
    {
      onFx ( () ->
      {
        tab.screenChanged (watcher);
        tab.tsoCommand (command);
        commandList ().getSelectionModel ().select (command);
      });
    }

    @Test
    @DisplayName ("o comando escolhido vai para o campo, e o botao liga")
    void chosenCommand ()
    {
      chooseCommand (ordinaryScreen (), "TSO LISTC");

      assertEquals ("TSO LISTC", text ());
      assertFalse (executeDisabled ());
    }

    @Test
    @DisplayName ("teclado travado desliga o botao")
    void keyboardLocked ()
    {
      keyboard.locked = true;
      chooseCommand (ordinaryScreen (), "TSO LISTC");

      assertEquals ("TSO LISTC", text ());
      assertTrue (executeDisabled ());
    }

    @Test
    @DisplayName ("tela sem campo de comando TSO desliga o botao")
    void noCommandField ()
    {
      chooseCommand (new StubWatcher (null, false, ""), "TSO LISTC");

      assertEquals ("TSO LISTC", text ());
      assertTrue (executeDisabled ());
    }

    @Test
    @DisplayName ("texto vazio desliga o botao")
    void emptyCommand ()
    {
      // na tela do TSO todo comando entra na lista, inclusive o vazio
      chooseCommand (new StubWatcher (commandField, true, ""), "");

      assertEquals ("", text ());
      assertTrue (executeDisabled ());
    }

    @Test
    @DisplayName ("na aba selecionada, o evento de teclado recalcula o botao")
    void keyboardEventOnSelectedTab ()
    {
      select (tab);
      chooseCommand (ordinaryScreen (), "TSO LISTC");
      assertFalse (executeDisabled ());

      keyboard.locked = true;
      keyboardChanged (tab);
      assertTrue (executeDisabled ());

      keyboard.locked = false;
      keyboardChanged (tab);
      assertFalse (executeDisabled ());
    }

    @Test
    @DisplayName ("fora da aba selecionada, o evento de teclado nao mexe no botao")
    void keyboardEventOnHiddenTab ()
    {
      chooseCommand (ordinaryScreen (), "TSO LISTC");
      assertFalse (tab.isSelected ());

      keyboard.locked = true;
      keyboardChanged (tab);
      assertFalse (executeDisabled ());
    }

    @Test
    @DisplayName ("sem ScreenWatcher o botao desliga")
    void noScreenWatcher ()
    {
      select (tab);
      chooseCommand (ordinaryScreen (), "TSO LISTC");
      onFx ( () -> tab.screenChanged (null));

      keyboardChanged (tab);
      assertEquals ("TSO LISTC", text ());
      assertTrue (executeDisabled ());
    }

    @Test
    @DisplayName ("sem comando escolhido, o campo esvazia e o botao desliga")
    void noSelection ()
    {
      chooseCommand (ordinaryScreen (), "TSO LISTC");
      onFx ( () -> commandList ().getSelectionModel ().clearSelection ());

      assertEquals ("", text ());
      assertTrue (executeDisabled ());
    }

    @Test
    @DisplayName ("apagar desliga o botao mesmo quando a regra o ligaria")
    void eraseIgnoresTheRule ()
    {
      // a tela e o teclado deixariam o botao ligado; e o apagar que o desliga
      chooseCommand (ordinaryScreen (), "TSO LISTC");
      onFx ( () -> tab.eraseCommand ());

      assertEquals ("", text ());
      assertTrue (executeDisabled ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("a aba Batch Jobs")
  class TheBatchJobsTab
  // ---------------------------------------------------------------------------------//
  {
    private BatchJobTab tab;

    @BeforeEach
    void createTab ()
    {
      tab = onFx ( () -> new BatchJobTab (keyboard, tsoCommand));
    }

    private void chooseJob (ScreenWatcher watcher, int jobNumber)
    {
      onFx ( () ->
      {
        tab.screenChanged (watcher);
        @SuppressWarnings ("unchecked")
        TableView<BatchJob> table = (TableView<BatchJob>) tab.getContent ();
        table.getSelectionModel ().select (tab.getBatchJob (jobNumber).orElseThrow ());
      });
    }

    @Test
    @DisplayName ("job ainda rodando: o campo esvazia e o botao desliga")
    void runningJob ()
    {
      onFx ( () -> tab.batchJobSubmitted (123, "DMOLONYB"));
      chooseJob (ordinaryScreen (), 123);

      assertEquals ("", text ());
      assertTrue (executeDisabled ());
    }

    @Test
    @DisplayName ("job terminado sem OUTLIST: o comando OUT, com TSO na frente")
    void completedJob ()
    {
      onFx ( () ->
      {
        tab.batchJobSubmitted (123, "DMOLONYB");
        tab.batchJobEnded (123, "DMOLONYB", "10:15", 0);
      });
      chooseJob (ordinaryScreen (), 123);

      assertEquals ("TSO OUT DMOLONYB(JOB00123) PRINT(JOB00123)", text ());
      assertFalse (executeDisabled ());
    }

    @Test
    @DisplayName ("na tela do TSO o comando vai sem o TSO na frente")
    void completedJobOnTsoScreen ()
    {
      onFx ( () ->
      {
        tab.batchJobSubmitted (123, "DMOLONYB");
        tab.batchJobFailed (123, "DMOLONYB", "10:15");
      });
      chooseJob (new StubWatcher (commandField, true, ""), 123);

      assertEquals ("OUT DMOLONYB(JOB00123) PRINT(JOB00123)", text ());
    }

    @Test
    @DisplayName ("com OUTLIST: o IND$FILE GET dele, em ASCII CRLF")
    void jobWithOutlist ()
    {
      onFx ( () ->
      {
        tab.batchJobSubmitted (123, "DMOLONYB");
        tab.batchJobEnded (123, "DMOLONYB", "10:15", 4);
        tab.tsoCommand ("TSO OUT DMOLONYB(JOB00123) PRINT(OUT123)");
      });
      chooseJob (ordinaryScreen (), 123);

      assertEquals ("TSO IND$FILE GET OUT123.OUTLIST ASCII CRLF", text ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("a aba Datasets")
  class TheDatasetsTab
  // ---------------------------------------------------------------------------------//
  {
    private DatasetTab tab;

    @BeforeEach
    void createTab ()
    {
      tab = onFx ( () -> new DatasetTab (keyboard, tsoCommand));
    }

    // a arvore guarda o membro de PDS sob o PDS; o pai e expandido para a linha existir
    private void chooseDataset (StubWatcher watcher, String... names)
    {
      for (String name : names)
        watcher.datasets.add (new DatasetSummary (name));

      onFx ( () ->
      {
        tab.screenChanged (watcher);
        @SuppressWarnings ("unchecked")
        TreeTableView<TableDataset> tree = (TreeTableView<TableDataset>) tab.getContent ();
        String wanted = names[names.length - 1];
        TreeItem<TableDataset> item = find (tree.getRoot (), wanted);
        assertNotNull (item, wanted);
        if (item.getParent () != tree.getRoot ())
          item.getParent ().setExpanded (true);
        tree.getSelectionModel ().select (item);
      });
    }

    private TreeItem<TableDataset> find (TreeItem<TableDataset> parent, String name)
    {
      for (TreeItem<TableDataset> child : parent.getChildren ())
      {
        if (name.equals (child.getValue ().getDatasetName ()))
          return child;
        TreeItem<TableDataset> found = find (child, name);
        if (found != null)
          return found;
      }
      return null;
    }

    @Test
    @DisplayName ("dataset de fora do prefixo vai entre aspas")
    void foreignDataset ()
    {
      chooseDataset (new StubWatcher (commandField, false, "USER01"), "SYS1.MACLIB");

      assertEquals ("TSO IND$FILE GET 'SYS1.MACLIB'", text ());
      assertFalse (executeDisabled ());
    }

    @Test
    @DisplayName ("dataset do prefixo perde o prefixo")
    void ownDataset ()
    {
      chooseDataset (new StubWatcher (commandField, false, "USER01"), "USER01.SOURCE");

      assertEquals ("TSO IND$FILE GET SOURCE", text ());
    }

    @Test
    @DisplayName ("membro de CNTL ou JCL vai em ASCII CRLF, e na tela do TSO sem o TSO")
    void jclMember ()
    {
      chooseDataset (new StubWatcher (commandField, true, "USER01"),
                     "USER01.JCL.CNTL(JOBCARD)");

      assertEquals ("IND$FILE GET JCL.CNTL(JOBCARD) ASCII CRLF", text ());
    }

    @Test
    @DisplayName ("o dataset que e so o prefixo esvazia o campo e desliga o botao")
    void prefixOnly ()
    {
      chooseDataset (new StubWatcher (commandField, false, "USER01"), "USER01");

      assertEquals ("", text ());
      assertTrue (executeDisabled ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("a aba Transfers")
  class TheTransfersTab
  // ---------------------------------------------------------------------------------//
  {
    private TransfersTab tab;

    @BeforeEach
    void createTab ()
    {
      tab = onFx ( () -> new TransfersTab (keyboard, tsoCommand));
      onFx ( () -> tab.screenChanged (ordinaryScreen ()));
    }

    @Test
    @DisplayName ("com Local Files marcado, o arquivo escolhido vai para o campo")
    void selectedFile ()
    {
      select (tab);
      onFx ( () ->
      {
        tab.fileSelected ("REPORT.TXT");
        tab.datasetSelected (new TableDataset ("USER01.SOURCE"));
      });

      assertEquals ("REPORT.TXT", text ());
      assertFalse (executeDisabled ());
    }

    @Test
    @DisplayName ("marcar Datasets poe o dataset escolhido no campo")
    void datasetsToggle ()
    {
      onFx ( () ->
      {
        tab.datasetSelected (new TableDataset ("USER01.SOURCE"));
        radio ("Datasets").fire ();
      });

      assertEquals ("USER01.SOURCE", text ());
    }

    private RadioButton radio (String label)
    {
      RadioButton button = radio ((Parent) tab.getContent (), label);
      assertNotNull (button, label);
      return button;
    }

    private RadioButton radio (Parent parent, String label)
    {
      for (Node node : parent.getChildrenUnmodifiable ())
      {
        if (node instanceof RadioButton button && label.equals (button.getText ()))
          return button;
        if (node instanceof Parent child)
        {
          RadioButton found = radio (child, label);
          if (found != null)
            return found;
        }
      }
      return null;
    }
  }
}
