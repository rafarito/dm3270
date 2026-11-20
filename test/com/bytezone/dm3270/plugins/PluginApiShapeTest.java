package com.bytezone.dm3270.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// Garante compatibilidade binaria (nao apenas de fonte) para JARs de plugins ja compilados.
@SuppressWarnings ("deprecation")
// -----------------------------------------------------------------------------------//
@DisplayName ("A forma binaria da API de plugins")
class PluginApiShapeTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("Plugin")
  class PluginShape
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("continua sendo interface")
    void continuaSendoInterface ()
    // -------------------------------------------------------------------------------//
    {
      assertTrue (Plugin.class.isInterface ());
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("declara exatamente os seis metodos, com as assinaturas de sempre")
    void declaraOsSeisMetodos ()
    // -------------------------------------------------------------------------------//
    {
      assertEquals (List.of ("boolean doesAuto()", "boolean doesRequest()",
                             "void activate()", "void deactivate()",
                             "void processAuto(com.bytezone.dm3270.plugins.PluginData)",
                             "void processRequest(com.bytezone.dm3270.plugins.PluginData)"),
                    signaturesOf (Plugin.class));
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("os seis continuam default, nenhum abstrato")
    void osSeisContinuamDefault ()
    // -------------------------------------------------------------------------------//
    {
      for (Method method : declaredMethodsOf (Plugin.class))
        assertTrue (method.isDefault (),
                    method.getName () + " deixou de ser default - isso quebra a Regra 3");
    }
  }

  // Papeis tem metodos abstratos; Plugin fornece os defaults (JLS 13.5.3 preserva binario).
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("Os tres papeis")
  class RoleShape
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("Plugin estende os tres papeis, e por isso todo plugin ja e os tres")
    void pluginEstendeOsTresPapeis ()
    // -------------------------------------------------------------------------------//
    {
      assertEquals (List.of (Activatable.class, AutoPlugin.class, RequestPlugin.class),
                    List.of (Plugin.class.getInterfaces ()));
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("cada papel declara so os seus dois metodos, e nenhum e default")
    void cadaPapelDeclaraSoOsSeusDois ()
    // -------------------------------------------------------------------------------//
    {
      assertEquals (List.of ("void activate()", "void deactivate()"),
                    signaturesOf (Activatable.class));
      assertEquals (List.of ("boolean doesAuto()",
                             "void processAuto(com.bytezone.dm3270.plugins.PluginData)"),
                    signaturesOf (AutoPlugin.class));
      assertEquals (List.of ("boolean doesRequest()",
                             "void processRequest(com.bytezone.dm3270.plugins.PluginData)"),
                    signaturesOf (RequestPlugin.class));

      for (Class<?> role : List.of (Activatable.class, AutoPlugin.class, RequestPlugin.class))
        for (Method method : declaredMethodsOf (role))
          assertFalse (method.isDefault (), method.getName () + " virou default no papel");
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("nenhum papel conhece Plugin de volta")
    void nenhumPapelConhecePluginDeVolta ()
    // -------------------------------------------------------------------------------//
    {
      for (Class<?> role : List.of (Activatable.class, AutoPlugin.class, RequestPlugin.class))
        assertEquals (List.of (), List.of (role.getInterfaces ()));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("DefaultPlugin")
  class DefaultPluginShape
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("continua sendo classe abstrata que implementa Plugin")
    void continuaSendoClasseAbstrata ()
    // -------------------------------------------------------------------------------//
    {
      assertFalse (DefaultPlugin.class.isInterface ());
      assertTrue (Modifier.isAbstract (DefaultPlugin.class.getModifiers ()));
      assertTrue (List.of (DefaultPlugin.class.getInterfaces ()).contains (Plugin.class));
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("continua com construtor sem argumentos, que a reflexao do host exige")
    void continuaComConstrutorSemArgumentos () throws Exception
    // -------------------------------------------------------------------------------//
    {
      assertEquals (0, DefaultPlugin.class.getDeclaredConstructor ().getParameterCount ());
    }

    // Modificadores integram o contrato binario (static gera invokestatic nos JARs antigos).
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("os sete utilitarios continuam com assinatura e modificador de sempre")
    void osSeteUtilitariosContinuamIguais ()
    // -------------------------------------------------------------------------------//
    {
      assertEquals (List.of ("package-private static java.lang.String toHex(byte[])",
                             "protected java.lang.String getMD5(byte[])",
                             "protected static int countModifiableFields"
                                 + "(com.bytezone.dm3270.plugins.PluginData)",
                             "protected static java.util.List getAlphanumericFields"
                                 + "(com.bytezone.dm3270.plugins.PluginData)",
                             "protected static java.util.List getModifiableFields"
                                 + "(com.bytezone.dm3270.plugins.PluginData)",
                             "protected static java.util.List getNumericFields"
                                 + "(com.bytezone.dm3270.plugins.PluginData)",
                             "protected static java.util.List getProtectedFields"
                                 + "(com.bytezone.dm3270.plugins.PluginData)"),
                    describedMembersOf (DefaultPlugin.class));
    }
  }

  // Filtra sinteticos como $jacocoInit, injetados pelo JaCoCo em tempo de execucao.
  // ---------------------------------------------------------------------------------//
  private static List<Method> declaredMethodsOf (Class<?> type)
  // ---------------------------------------------------------------------------------//
  {
    return Arrays.stream (type.getDeclaredMethods ()).filter (method -> !method.isSynthetic ())
        .collect (Collectors.toList ());
  }

  // Ordena porque Class.getDeclaredMethods() nao garante ordem deterministica.
  // ---------------------------------------------------------------------------------//
  private static List<String> signaturesOf (Class<?> type)
  // ---------------------------------------------------------------------------------//
  {
    return declaredMethodsOf (type).stream ()
        .map (method -> method.getReturnType ().getSimpleName () + " " + signature (method))
        .sorted ().collect (Collectors.toList ());
  }

  // ---------------------------------------------------------------------------------//
  private static List<String> describedMembersOf (Class<?> type)
  // ---------------------------------------------------------------------------------//
  {
    return declaredMethodsOf (type).stream ()
        .map (method -> modifiersOf (method) + " " + method.getReturnType ().getName () + " "
            + signature (method))
        .sorted ().collect (Collectors.toList ());
  }

  // ---------------------------------------------------------------------------------//
  private static String signature (Method method)
  // ---------------------------------------------------------------------------------//
  {
    return method.getName () + "("
        + Arrays.stream (method.getParameterTypes ()).map (Class::getCanonicalName)
            .collect (Collectors.joining (","))
        + ")";
  }

  // ---------------------------------------------------------------------------------//
  private static String modifiersOf (Method method)
  // ---------------------------------------------------------------------------------//
  {
    int modifiers = method.getModifiers ();
    StringBuilder text = new StringBuilder ();

    if (Modifier.isPublic (modifiers))
      text.append ("public");
    else if (Modifier.isProtected (modifiers))
      text.append ("protected");
    else if (Modifier.isPrivate (modifiers))
      text.append ("private");
    else
      text.append ("package-private");

    if (Modifier.isStatic (modifiers))
      text.append (" static");

    return text.toString ();
  }
}
