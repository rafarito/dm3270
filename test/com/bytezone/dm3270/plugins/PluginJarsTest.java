package com.bytezone.dm3270.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bytezone.dm3270.testing.SyntheticPluginJar;

/*
 * A rede do colaborador que saiu do PluginsStage no passo 9.
 *
 * REPARE NO QUE ESTA CLASSE NAO TEM: @ExtendWith (JavaFxToolkit.class). Ela exercita o
 * carregamento de JARs de plugin inteiro - listar, montar os loaders, descobrir, carregar e
 * fechar - sem subir toolkit grafico nenhum, porque nada disso precisava de janela. Enquanto
 * morava dentro de uma javafx.stage.Stage, so podia ser testado com o toolkit de pe.
 *
 * O PluginsStageDispatchTest continua precisando do toolkit, e isso esta certo: o que sobrou
 * la e menu, preferencias e formulario.
 */
// -----------------------------------------------------------------------------------//
@DisplayName ("PluginJars - os JARs da pasta de plugins, sem janela")
class PluginJarsTest
// -----------------------------------------------------------------------------------//
{
  @TempDir
  private Path directory;

  private PluginJars jars;

  /*
   * Obrigatorio: no Windows um URLClassLoader aberto mantem o JAR mapeado e a remocao do
   * @TempDir pelo JUnit falha com DirectoryNotEmptyException.
   */
  // ---------------------------------------------------------------------------------//
  @AfterEach
  void closeLoaders ()
  // ---------------------------------------------------------------------------------//
  {
    if (jars != null)
      jars.close ();
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("A pasta")
  class Directory
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("uma pasta vazia nao descobre nada e nao quebra")
    void pastaVaziaNaoDescobreNada ()
    // -------------------------------------------------------------------------------//
    {
      jars = new PluginJars (directory);

      assertEquals (List.of (), jars.discover ());
      assertNull (jars.loadFromOwningJar ("com.example.QualquerCoisa"));
      assertEquals (directory, jars.getDirectory ());
    }

    /*
     * So arquivos .jar entram, e este caso precisou de duas tentativas.
     *
     * A primeira versao punha um .txt e um .properties na pasta e afirmava que a descoberta
     * achava um plugin so. Ela passava - e o mutante do PIT que fazia o filtro devolver sempre
     * true TAMBEM passava: com ele os dois arquivos entram na lista, mas o new JarFile falha
     * neles com IOException, que a varredura loga e engole. O resultado final era o mesmo, e a
     * assercao nao distinguia nada.
     *
     * O que distingue e um arquivo que E um JAR valido, com um plugin dentro, mas NAO termina
     * em .jar. O codigo certo o ignora pelo nome; o mutante o abre e descobre o segundo plugin.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("so a extensao .jar entra, mesmo que o arquivo seja um JAR valido")
    void soAExtensaoJarEntra () throws Exception
    // -------------------------------------------------------------------------------//
    {
      writePlugin ("com.example.um", "Primeiro", "Primeiro.jar");
      writePlugin ("com.example.dois", "Segundo", "Segundo.zip");
      Files.writeString (directory.resolve ("LEIAME.txt"), "isto nao e um JAR");

      jars = new PluginJars (directory);
      List<String[]> discovered = jars.discover ();

      assertEquals (1, discovered.size ());
      assertEquals ("com.example.um.Primeiro", discovered.get (0)[1]);
      assertNull (jars.loadFromOwningJar ("com.example.dois.Segundo"));
    }

    /*
     * A pasta e CRIADA quando nao existe. E o que faz o dm3270 subir numa instalacao nova sem
     * que ninguem precise criar plugins/ a mao.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("uma pasta que nao existe e criada")
    void pastaInexistenteECriada ()
    // -------------------------------------------------------------------------------//
    {
      Path nova = directory.resolve ("ainda-nao-existe");

      jars = new PluginJars (nova);

      assertTrue (Files.isDirectory (nova));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("A descoberta")
  class Discovery
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("acha quem implementa Plugin, com nome simples e qualificado")
    void achaQuemImplementaPlugin () throws Exception
    // -------------------------------------------------------------------------------//
    {
      writePlugin ("com.example.um", "Primeiro", "Primeiro.jar");

      jars = new PluginJars (directory);
      List<String[]> discovered = jars.discover ();

      assertEquals (1, discovered.size ());
      assertEquals ("Primeiro", discovered.get (0)[0]);
      assertEquals ("com.example.um.Primeiro", discovered.get (0)[1]);
    }

    /*
     * As duas bordas do filtro: quem nao implementa Plugin fica de fora, e uma classe
     * ANINHADA tambem - a varredura pula toda entrada de JAR cujo nome contenha cifrao. Um
     * plugin declarado como classe aninhada e invisivel, e isso e comportamento antigo.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("ignora quem nao e Plugin e ignora classe aninhada")
    void ignoraQuemNaoEPluginEAninhada () throws Exception
    // -------------------------------------------------------------------------------//
    {
      new SyntheticPluginJar ()
          .add ("com.example.fora.Comum", """
              package com.example.fora;

              public class Comum
              {
                public Comum () { }

                public static class Dentro implements com.bytezone.dm3270.plugins.Plugin
                {
                  public Dentro () { }
                }
              }
              """)
          .writeTo (directory, "Fora.jar");

      jars = new PluginJars (directory);

      assertEquals (List.of (), jars.discover ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("O loader de cada JAR")
  class Loaders
  // ---------------------------------------------------------------------------------//
  {
    /*
     * O nucleo da correcao do passo 9, afirmado aqui sem passar pelo host: dois JARs com a
     * MESMA classe auxiliar, e cada plugin resolvendo a copia que veio no proprio JAR.
     *
     * A verificacao vai pelo class loader de cada plugin - que e por onde o JVM resolve as
     * referencias que ele faz -, e nao pelo que o PluginJars devolveria para o nome da classe
     * auxiliar. Sao coisas diferentes, e e a primeira que importa em execucao.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("cada plugin resolve a copia que veio no proprio JAR")
    void cadaPluginResolveACopiaDoProprioJar () throws Exception
    // -------------------------------------------------------------------------------//
    {
      writeStamped ("a", "A");
      writeStamped ("b", "B");

      jars = new PluginJars (directory);

      Class<?> pluginA = jars.loadFromOwningJar ("com.example.a.PluginA");
      Class<?> pluginB = jars.loadFromOwningJar ("com.example.b.PluginB");

      assertNotSame (pluginA.getClassLoader (), pluginB.getClassLoader ());
      assertEquals ("A", stampSeenBy (pluginA));
      assertEquals ("B", stampSeenBy (pluginB));
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("uma classe que nao esta em JAR nenhum devolve null")
    void classeAusenteDevolveNull () throws Exception
    // -------------------------------------------------------------------------------//
    {
      writePlugin ("com.example.um", "Primeiro", "Primeiro.jar");

      jars = new PluginJars (directory);

      assertNull (jars.loadFromOwningJar ("com.example.um.NaoExiste"));
    }

    /*
     * O terceiro degrau da busca: um plugin continua achando uma classe que mora noutro JAR
     * da pasta. Se o loader olhasse so o proprio JAR, isto quebraria em silencio, no meio de
     * uma sessao.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("um plugin acha a biblioteca solta noutro JAR da pasta")
    void pluginAchaBibliotecaSolta () throws Exception
    // -------------------------------------------------------------------------------//
    {
      SyntheticPluginJar sources = new SyntheticPluginJar ()
          .add ("com.example.lib.Greeting", """
              package com.example.lib;

              public class Greeting
              {
                public static String text ()
                {
                  return "biblioteca";
                }
              }
              """)
          .add ("com.example.user.PluginUser", """
              package com.example.user;

              import com.bytezone.dm3270.plugins.Plugin;

              public class PluginUser implements Plugin
              {
                public PluginUser () { }

                public static String greeting ()
                {
                  return com.example.lib.Greeting.text ();
                }
              }
              """);

      sources.writeTo (directory, "lib.jar", "com.example.lib.Greeting");
      sources.writeTo (directory, "PluginUser.jar", "com.example.user.PluginUser");

      jars = new PluginJars (directory);
      Class<?> plugin = jars.loadFromOwningJar ("com.example.user.PluginUser");

      assertEquals ("biblioteca", plugin.getMethod ("greeting").invoke (null));
    }
  }

  // ---------------------------------------------------------------------------------//
  //  Apoio
  // ---------------------------------------------------------------------------------//

  // ---------------------------------------------------------------------------------//
  private void writePlugin (String pkg, String name, String jarName) throws Exception
  // ---------------------------------------------------------------------------------//
  {
    new SyntheticPluginJar ().add (pkg + "." + name, """
        package %s;

        import com.bytezone.dm3270.plugins.Plugin;

        public class %s implements Plugin
        {
          public %s () { }
        }
        """.formatted (pkg, name, name)).writeTo (directory, jarName);
  }

  /*
   * Um JAR com um plugin e uma classe auxiliar de nome qualificado FIXO - o mesmo nos dois
   * JARs -, cuja unica diferenca e a letra que ela devolve.
   */
  // ---------------------------------------------------------------------------------//
  private void writeStamped (String suffix, String letter) throws Exception
  // ---------------------------------------------------------------------------------//
  {
    String plugin = "Plugin" + letter;

    new SyntheticPluginJar ()
        .add ("com.example.shared.Stamp", """
            package com.example.shared;

            public class Stamp
            {
              public static String mark ()
              {
                return "%s";
              }
            }
            """.formatted (letter))
        .add ("com.example." + suffix + "." + plugin, """
            package com.example.%s;

            import com.bytezone.dm3270.plugins.Plugin;

            public class %s implements Plugin
            {
              public %s () { }
            }
            """.formatted (suffix, plugin, plugin))
        .writeTo (directory, plugin + ".jar");
  }

  /*
   * Resolve com.example.shared.Stamp PELO class loader do plugin, que e o caminho que o JVM
   * usa para as referencias que o proprio plugin faz.
   */
  // ---------------------------------------------------------------------------------//
  private static String stampSeenBy (Class<?> plugin) throws Exception
  // ---------------------------------------------------------------------------------//
  {
    Class<?> stamp =
        Class.forName ("com.example.shared.Stamp", true, plugin.getClassLoader ());

    return (String) stamp.getMethod ("mark").invoke (null);
  }
}
