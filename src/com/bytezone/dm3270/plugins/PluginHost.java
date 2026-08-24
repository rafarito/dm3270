package com.bytezone.dm3270.plugins;

import java.util.List;
import java.util.Optional;

import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.screen.Cursor;
import com.bytezone.dm3270.screen.Field;
import com.bytezone.dm3270.screen.ScreenDimensions;

/*
 * O que o subsistema de plugins precisa da tela.
 *
 * O PluginsStage guardava a classe Screen inteira - 1.087 linhas que estendem
 * javafx.scene.canvas.Canvas - e usava oito coisas dela. Guardava tambem o FieldManager,
 * pedido a Screen a cada chamada, so para converter os campos da tela no formato que a API
 * de plugins expoe.
 *
 * Como Screen mora em display e display precisa nomear PluginsStage - a tela possui a janela
 * de plugins, como possui todo o resto - as duas pontas se referenciavam e o par
 * display <-> plugins era um ciclo mutuo. A porta esta declarada AQUI, no lado que consome,
 * que e o que inverte a dependencia: plugins deixa de conhecer display, e display continua
 * conhecendo plugins numa direcao so.
 *
 * O inventario foi levantado do codigo, nao imaginado: processPluginAuto usa cinco membros,
 * processPluginRequest usa quatro e processReply usa cinco, com sobreposicao. Nada aqui
 * devolve um colaborador inteiro - getFields () devolve a lista de campos e a traducao para
 * PluginField acontece do lado de ca, em PluginFields.
 */
// -----------------------------------------------------------------------------------//
public interface PluginHost
// -----------------------------------------------------------------------------------//
{
  ScreenDimensions getScreenDimensions ();

  boolean isKeyboardLocked ();

  Cursor getScreenCursor ();

  List<Field> getFields ();

  Optional<Field> getFieldAt (int position);

  void lockKeyboard (String keyName);

  void setAID (byte aid);

  AIDCommand readModifiedFields ();
}
