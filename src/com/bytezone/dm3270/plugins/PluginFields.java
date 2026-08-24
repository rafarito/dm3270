package com.bytezone.dm3270.plugins;

import java.util.ArrayList;
import java.util.List;

import com.bytezone.dm3270.screen.Field;

/*
 * A traducao de um Field do modelo de tela para o PluginField que a API de plugins expoe.
 *
 * Morou dentro do proprio Field, e era a unica coisa que fazia o modelo de tela conhecer o
 * pacote plugins. A Fase 2 a mudou para o FieldManager, o que resolveu aquele lado mas
 * deixou display nomeando plugins a partir de uma classe que so agrupa posicoes de tela em
 * campos - agrupar campos e traduzir para uma API externa sao duas responsabilidades.
 *
 * Aqui e o lugar: quem define o formato PluginField e quem sabe converter para ele. Com isto
 * o FieldManager para de importar plugins, que e o pre-requisito para ele poder mudar de
 * pacote mais adiante.
 *
 * As duas linhas que calculavam row e column continuam fora: o ScreenLocation e montado a
 * partir de firstLocation, e nada mais le esses dois valores separadamente.
 */
// -----------------------------------------------------------------------------------//
public final class PluginFields
// -----------------------------------------------------------------------------------//
{
  private PluginFields ()
  {
  }

  // ---------------------------------------------------------------------------------//
  public static PluginData toPluginData (int sequence, ScreenLocation cursorLocation,
      List<Field> fields)
  // ---------------------------------------------------------------------------------//
  {
    List<PluginField> pluginFields = new ArrayList<> ();
    int count = 0;

    for (Field field : fields)
      pluginFields.add (toPluginField (field, count++));

    return new PluginData (sequence, cursorLocation, pluginFields);
  }

  // ---------------------------------------------------------------------------------//
  private static PluginField toPluginField (Field field, int fieldSequence)
  // ---------------------------------------------------------------------------------//
  {
    ScreenLocation screenLocation = new ScreenLocation (field.getFirstLocation ());

    return new PluginField (fieldSequence, screenLocation, field.getDisplayLength (),
        field.isProtected (), field.isAlphanumeric (), field.isVisible (),
        field.isModified (), field.getText ());
  }
}
