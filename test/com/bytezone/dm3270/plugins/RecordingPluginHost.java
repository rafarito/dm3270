package com.bytezone.dm3270.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.display.HeadlessScreenTarget;
import com.bytezone.dm3270.screen.Cursor;
import com.bytezone.dm3270.screen.Field;
import com.bytezone.dm3270.screen.ScreenDimensions;

/*
 * A tela que o subsistema de plugins enxerga, dublada. Implementa a porta PluginHost inteira
 * - oito metodos - e grava no PluginProbe, na MESMA lista dos dubles de plugin, para que um
 * caso possa afirmar a ordem entre o que o plugin faz e o que a tela recebe.
 *
 * O readModifiedFields devolve o AIDCommand que o teste combinar, ou null: e por ele que
 * passa a diferenca entre o caminho que trava o teclado e envia, e o que nao faz nada.
 */
// -----------------------------------------------------------------------------------//
public class RecordingPluginHost implements PluginHost
// -----------------------------------------------------------------------------------//
{
  private final ScreenDimensions screenDimensions = new ScreenDimensions (24, 80);
  private final Cursor cursor = new RecordingCursor ();
  private final List<Field> fields = new ArrayList<> ();

  private AIDCommand reply;

  // ---------------------------------------------------------------------------------//
  public void setReply (AIDCommand reply)
  // ---------------------------------------------------------------------------------//
  {
    this.reply = reply;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public ScreenDimensions getScreenDimensions ()
  // ---------------------------------------------------------------------------------//
  {
    return screenDimensions;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public boolean isKeyboardLocked ()
  // ---------------------------------------------------------------------------------//
  {
    return false;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public Cursor getScreenCursor ()
  // ---------------------------------------------------------------------------------//
  {
    return cursor;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public List<Field> getFields ()
  // ---------------------------------------------------------------------------------//
  {
    return fields;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public Optional<Field> getFieldAt (int position)
  // ---------------------------------------------------------------------------------//
  {
    return Optional.empty ();
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void lockKeyboard (String keyName)
  // ---------------------------------------------------------------------------------//
  {
    PluginProbe.record ("host.lockKeyboard:" + keyName);
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void setAID (byte aid)
  // ---------------------------------------------------------------------------------//
  {
    PluginProbe.record ("host.setAID:" + aid);
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public AIDCommand readModifiedFields ()
  // ---------------------------------------------------------------------------------//
  {
    PluginProbe.record ("host.readModifiedFields");
    return reply;
  }

  /*
   * Cursor e classe, nao interface, e nenhum dos metodos usados e final - entao o duble e
   * uma subclasse que grava em vez de executar. E de proposito que ele NAO executa: esta rede
   * congela o DESPACHO, e a semantica do cursor ja tem o CursorTest, com 32 casos. O
   * precedente e o RecordingCursor do ConsoleKeyPressTest, no passo 8.
   *
   * O construtor da superclasse so guarda dois campos, mas recebe colaboradores de verdade,
   * para que o duble quebre alto se algum dia ele passar a usa-los.
   */
  // ---------------------------------------------------------------------------------//
  private static final class RecordingCursor extends Cursor
  // ---------------------------------------------------------------------------------//
  {
    RecordingCursor ()
    {
      super (new HeadlessScreenTarget (), new ScreenDimensions (24, 80));
    }

    @Override
    public int getLocation ()
    {
      return 0;
    }

    @Override
    public boolean isVisible ()
    {
      return false;
    }

    @Override
    public void setVisible (boolean visible)
    {
      PluginProbe.record ("cursor.setVisible:" + visible);
    }

    @Override
    public void moveTo (int newPosition)
    {
      PluginProbe.record ("cursor.moveTo:" + newPosition);
    }
  }
}
