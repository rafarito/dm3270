package com.bytezone.dm3270.utilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.bytezone.dm3270.testing.JavaFxToolkit;

/*
 * Caracterizacao do Site.
 *
 * Site e a entidade de configuracao de conexao - host, porta, modelo de terminal, SSL - e
 * hoje e composta inteiramente de widgets JavaFX. A refatoracao vai separa-la em tres: um
 * valor de dominio imutavel, um validador explicito e um formulario que fica com os
 * widgets.
 *
 * Estes testes congelam o comportamento atual ANTES dessa separacao, incluindo o que e
 * claramente defeituoso. A regra da refatoracao e preservar comportamento, entao o
 * formulario resultante precisa continuar fazendo exatamente isto:
 *
 *   - getPort e getModel nao apenas leem: eles CORRIGEM o widget em caso de valor
 *     invalido, gravando "23" e "2" de volta no campo. Ler duas vezes produz efeitos
 *     diferentes na primeira e na segunda chamada.
 *   - toString chama getPort, entao imprimir um Site com porta invalida altera o Site.
 *   - o construtor deixa porta e modelo em branco quando recebe os valores default e o
 *     nome esta vazio.
 *
 * Nada disso e corrigido aqui. Os defeitos ficam registrados para decisao separada.
 */
// -----------------------------------------------------------------------------------//
@ExtendWith (JavaFxToolkit.class)
@DisplayName ("Site - configuracao de conexao feita de widgets")
class SiteTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  private static Site site (String name, int port, int model)
  // ---------------------------------------------------------------------------------//
  {
    return new Site (name, "host.example.com", port, false, model, false, false, false, "");
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("construcao")
  class Construction
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("guarda os valores informados")
    void keepsGivenValues ()
    {
      Site site = new Site ("prod", "mvs.example.com", 992, true, 4, true, true, true,
          "/tmp/dm3270");

      assertEquals ("prod", site.getName ());
      assertEquals ("mvs.example.com", site.getURL ());
      assertEquals (992, site.getPort ());
      assertTrue (site.getExtended ());
      assertEquals (4, site.getModel ());
      assertTrue (site.getPlugins ());
      assertTrue (site.getSsl ());
      assertTrue (site.getTrustAll ());
      assertEquals ("/tmp/dm3270", site.getFolder ());
    }

    @Test
    @DisplayName ("com nome vazio, a porta 23 fica em branco no widget")
    void blankPortForDefaultOnUnnamedSite ()
    {
      Site site = site ("", 23, 3);

      assertEquals ("", site.port.getText (), "o widget fica em branco");
      assertEquals (23, site.getPort (), "mas a leitura ainda devolve 23");
    }

    @Test
    @DisplayName ("com nome vazio, o modelo 2 fica em branco no widget")
    void blankModelForDefaultOnUnnamedSite ()
    {
      Site site = site ("", 992, 2);

      assertEquals ("", site.model.getText (), "o widget fica em branco");
      assertEquals (2, site.getModel (), "mas a leitura ainda devolve 2");
    }

    @Test
    @DisplayName ("com nome preenchido, os defaults aparecem no widget")
    void namedSiteKeepsDefaultsVisible ()
    {
      Site site = site ("prod", 23, 2);

      assertEquals ("23", site.port.getText ());
      assertEquals ("2", site.model.getText ());
    }
  }

  /*
   * O ponto central: estes getters tem efeito colateral na UI. Estao congelados como
   * defeito conhecido, nao como comportamento desejavel.
   */
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("getPort corrige o widget silenciosamente")
  class PortReading
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("valor valido e devolvido sem tocar no widget")
    void validPortIsUntouched ()
    {
      Site site = site ("prod", 992, 2);

      assertEquals (992, site.getPort ());
      assertEquals ("992", site.port.getText ());
    }

    @ParameterizedTest (name = "porta \"{0}\"")
    @DisplayName ("valor invalido vira 23 e o widget e reescrito")
    @ValueSource (strings = { "0", "-1", "abc", "", "  ", "99.5" })
    void invalidPortIsRewritten (String raw)
    {
      Site site = site ("prod", 992, 2);
      site.port.setText (raw);

      assertEquals (23, site.getPort (), "a leitura devolve o default");
      assertEquals ("23", site.port.getText (), "e o widget foi reescrito pelo getter");
    }

    @Test
    @DisplayName ("ler duas vezes produz efeitos diferentes")
    void readingTwiceDiffers ()
    {
      Site site = site ("prod", 992, 2);
      site.port.setText ("0");

      assertEquals ("0", site.port.getText (), "antes da primeira leitura");
      site.getPort ();
      assertEquals ("23", site.port.getText (), "a primeira leitura corrigiu o widget");
      site.getPort ();
      assertEquals ("23", site.port.getText (), "a segunda ja encontra o valor corrigido");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("getModel corrige o widget silenciosamente")
  class ModelReading
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "modelo {0}")
    @DisplayName ("de 2 a 5 e aceito")
    @ValueSource (ints = { 2, 3, 4, 5 })
    void validModelsAreAccepted (int model)
    {
      Site site = site ("prod", 992, model);

      assertEquals (model, site.getModel ());
      assertEquals (String.valueOf (model), site.model.getText ());
    }

    @ParameterizedTest (name = "modelo \"{0}\"")
    @DisplayName ("fora da faixa vira 2 e o widget e reescrito")
    @ValueSource (strings = { "1", "0", "-3", "6", "99", "abc", "" })
    void invalidModelIsRewritten (String raw)
    {
      Site site = site ("prod", 992, 3);
      site.model.setText (raw);

      assertEquals (2, site.getModel ());
      assertEquals ("2", site.model.getText ());
    }
  }

  /*
   * Os dois arrays paralelos com buracos: o indice e compartilhado entre eles, e cada
   * posicao existe em exatamente um dos dois. Quem consome precisa saber disso, o que e
   * parte do acoplamento que a refatoracao vai remover.
   */
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("acesso posicional aos widgets")
  class PositionalAccess
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("os campos de texto ocupam as posicoes 0, 1, 2, 4 e 8")
    void textFieldPositions ()
    {
      Site site = site ("prod", 992, 2);

      assertSame (site.name, site.getTextField (0));
      assertSame (site.url, site.getTextField (1));
      assertSame (site.port, site.getTextField (2));
      assertSame (site.model, site.getTextField (4));
      assertSame (site.folder, site.getTextField (8));
    }

    @Test
    @DisplayName ("as caixas de selecao ocupam as posicoes 3, 5, 6 e 7")
    void checkBoxPositions ()
    {
      Site site = site ("prod", 992, 2);

      assertSame (site.extended, site.getCheckBoxField (3));
      assertSame (site.plugins, site.getCheckBoxField (5));
      assertSame (site.ssl, site.getCheckBoxField (6));
      assertSame (site.trustAll, site.getCheckBoxField (7));
    }

    @Test
    @DisplayName ("cada posicao existe em exatamente um dos dois arrays")
    void positionsAreComplementary ()
    {
      Site site = site ("prod", 992, 2);

      for (int i = 0; i < 9; i++)
      {
        boolean isText = site.getTextField (i) != null;
        boolean isCheckBox = site.getCheckBoxField (i) != null;

        assertTrue (isText ^ isCheckBox,
            "a posicao " + i + " deve ser texto ou caixa, nunca ambos nem nenhum");
      }
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("toString")
  class Text
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("formata nome, url, porta e pasta")
    void formatsTheFields ()
    {
      Site site = new Site ("prod", "mvs.example.com", 992, false, 2, false, false, false,
          "/tmp/dm3270");

      assertEquals ("Site [name=prod, url=mvs.example.com, port=992, folder=/tmp/dm3270]",
          site.toString ());
    }

    @Test
    @DisplayName ("imprimir um site com porta invalida altera o site")
    void printingMutatesTheSite ()
    {
      Site site = site ("prod", 992, 2);
      site.port.setText ("0");

      assertTrue (site.toString ().contains ("port=23"));
      assertEquals ("23", site.port.getText (),
          "toString chama getPort, que reescreve o widget");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("caixas de selecao")
  class Flags
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("refletem o estado do widget")
    void reflectWidgetState ()
    {
      Site site = site ("prod", 992, 2);

      assertFalse (site.getExtended ());
      site.extended.setSelected (true);
      assertTrue (site.getExtended ());

      assertFalse (site.getSsl ());
      site.ssl.setSelected (true);
      assertTrue (site.getSsl ());
    }

    @Test
    @DisplayName ("os widgets sao publicos e mutaveis de fora")
    void widgetsAreExposed ()
    {
      Site site = site ("prod", 992, 2);

      assertNotNull (site.name);
      site.name.setText ("outro");

      assertEquals ("outro", site.getName (), "qualquer chamador pode reescrever o site");
    }

    @Test
    @DisplayName ("posicoes fora dos arrays paralelos nao tem widget")
    void unusedPositionsAreNull ()
    {
      Site site = site ("prod", 992, 2);

      assertNull (site.getTextField (3));
      assertNull (site.getCheckBoxField (0));
    }
  }
}
