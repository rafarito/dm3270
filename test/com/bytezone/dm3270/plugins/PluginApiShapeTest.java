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

/*
 * A FORMA BINARIA da API que os JARs de terceiros ja compilados enxergam.
 *
 * Esta classe existe porque compatibilidade de FONTE e compatibilidade de BINARIO sao coisas
 * diferentes, e a Regra 3 e sobre a segunda. Um plugin recompilado absorve, sem reclamar,
 * exatamente as mudancas que quebram um plugin ja compilado:
 *
 *   - transformar DefaultPlugin.getModifiableFields de "protected static" em metodo de
 *     instancia deixa toda subclasse compilando igual, e mata o invokestatic gravado dentro
 *     do DownloadDataset.jar com IncompatibleClassChangeError;
 *   - transformar DefaultPlugin de classe abstrata em interface faz o mesmo com o
 *     "extends com.bytezone.dm3270.plugins.DefaultPlugin" que esta no bytecode dos JARs
 *     instalados - conferido com javap;
 *   - transformar um default de Plugin em metodo abstrato quebra todo plugin que nao o
 *     implementa.
 *
 * Nenhuma dessas aparece num "mvn test" do repositorio de plugins, porque la tudo e
 * recompilado. Aqui aparecem.
 *
 * O passo 9 acrescenta interfaces e move utilitarios; e este o teste que diz se alguma dessas
 * mexidas saiu do que a Regra 3 permite.
 */
// O DefaultPlugin e deprecated de proposito - e a camada de compatibilidade com os JARs
// ja compilados -, e afirmar a forma binaria dela e justamente o que esta classe faz.
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

    /*
     * Os seis metodos, com o descritor de cada um. Nome e assinatura sao o contrato: mudar
     * um parametro reescreve o descritor e nenhum JAR antigo encontra mais o metodo.
     */
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

    /*
     * TODOS default. E isto que faz um JAR antigo - compilado quando a interface ja era
     * assim - continuar carregando sem implementar nada. Tornar qualquer um deles abstrato
     * quebra a Regra 3, e e a mudanca mais tentadora de todas para quem for "arrumar o ISP".
     */
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

  /*
   * Os papeis declaram os metodos ABSTRATOS - sao eles que dizem o que cada papel significa.
   * Quem devolve o default e o Plugin, e e por isso que acrescentar as superinterfaces nao
   * acrescentou metodo abstrato nenhum a ele (JLS 13.5.3) e os JARs antigos seguem
   * carregando.
   */
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("Os tres papeis")
  class RoleShape
  // ---------------------------------------------------------------------------------//
  {
    /*
     * A DIRECAO do extends, que foi escolhida com a medicao na mao: Plugin ESTENDE os tres,
     * em vez de os tres estenderem Plugin. Assim todo plugin que ja existiu passa a ser um
     * AutoPlugin e um RequestPlugin sem que uma linha de fonte mude em lugar nenhum - e, o
     * ponto, um "instanceof AutoPlugin" fica trivialmente verdadeiro, incapaz de filtrar
     * coisa alguma. O despacho estatico que o plano do passo 9 pedia deixa de ser possivel
     * POR CONSTRUCAO, e nao so por recomendacao.
     */
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

    /*
     * Nenhum dos tres estende Plugin - se estendessem, a direcao se inverteria e o
     * instanceof voltaria a ser um filtro util, que e justamente o que nao se quer.
     */
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
    /*
     * Os dois JARs instalados nesta maquina trazem
     * "extends com.bytezone.dm3270.plugins.DefaultPlugin" no bytecode - conferido com javap
     * no DownloadDataset.jar. Se a classe virar interface, ou sumir, os dois param de
     * carregar com IncompatibleClassChangeError ou NoClassDefFoundError.
     */
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

    /*
     * Os sete utilitarios, com o MODIFICADOR de cada um - e o modificador e parte do
     * contrato binario tanto quanto a assinatura. "protected static" chamado de uma
     * subclasse vira invokestatic; passar a metodo de instancia vira invokevirtual, e o
     * bytecode antigo nao acompanha.
     *
     * Medido: destes sete, so getModifiableFields e chamado por alguem - seis sitios em tres
     * plugins -, e ele duplica um metodo que o proprio PluginData ja tem. Os outros seis tem
     * zero referencias em qualquer um dos dois repositorios, e toHex ainda por cima e de
     * pacote, invisivel para um plugin em com.bytezone.plugins. Mesmo assim os sete ficam:
     * protected e API de terceiro, e remover e quebra de Regra 3, nao limpeza de codigo
     * morto.
     */
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

  // ---------------------------------------------------------------------------------//
  //  Apoio
  // ---------------------------------------------------------------------------------//

  /*
   * FILTRA OS SINTETICOS, e isto nao e detalhe: o JaCoCo instrumenta as classes durante a
   * suite e acrescenta um $jacocoInit (MethodHandles.Lookup, String, Class) a cada uma. Ele
   * aparece em getDeclaredMethods () como private static e NAO default, entao um teste de
   * reflexao que nao o filtre falha com "$jacocoInit deixou de ser default" - que foi
   * exatamente o que aconteceu ao escrever este arquivo.
   *
   * Vale para qualquer teste de reflexao neste projeto, nao so para este.
   */
  // ---------------------------------------------------------------------------------//
  private static List<Method> declaredMethodsOf (Class<?> type)
  // ---------------------------------------------------------------------------------//
  {
    return Arrays.stream (type.getDeclaredMethods ()).filter (method -> !method.isSynthetic ())
        .collect (Collectors.toList ());
  }

  /*
   * Ordenado pelo texto, para que o teste nao dependa da ordem em que a JVM devolve os
   * metodos declarados - que nao e especificada.
   */
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
