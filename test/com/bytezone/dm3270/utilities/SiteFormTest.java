package com.bytezone.dm3270.utilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.bytezone.dm3270.testing.JavaFxToolkit;

/*
 * Caracterizacao do SiteForm - a classe que era o Site.
 *
 * O SiteForm e a configuracao de conexao - host, porta, modelo de terminal, SSL - composta
 * inteiramente de widgets JavaFX. O nome mudou neste commit para liberar o nome Site, que
 * passa a ser a interface pura que os consumidores nomeiam; os widgets ficam aqui.
 *
 * Estes testes congelam o comportamento atual, incluindo o que e claramente defeituoso. A
 * regra da refatoracao e preservar comportamento, entao o formulario precisa continuar
 * fazendo exatamente isto:
 *
 *   - getPort e getModel nao apenas leem: eles CORRIGEM o widget em caso de valor
 *     invalido, gravando "23" e "2" de volta no campo. Ler duas vezes produz efeitos
 *     diferentes na primeira e na segunda chamada.
 *   - toString chama getPort, entao imprimir um SiteForm com porta invalida altera o
 *     objeto. O texto continua comecando por "Site [", que e saida observavel.
 *   - o construtor deixa porta e modelo em branco quando recebe os valores default e o
 *     nome esta vazio.
 *   - os widgets sao live, e quem guarda um SiteForm le o valor no momento do uso. E a
 *     propriedade coberta pelo @Nested do fim, e a razao de o Site ter virado interface
 *     em vez do record que o plano previa.
 *
 * Nada disso e corrigido aqui. Os defeitos ficam registrados para decisao separada.
 */
// -----------------------------------------------------------------------------------//
@ExtendWith (JavaFxToolkit.class)
@DisplayName ("SiteForm - a configuracao de conexao, feita de widgets")
class SiteFormTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  private static SiteForm site (String name, int port, int model)
  // ---------------------------------------------------------------------------------//
  {
    return new SiteForm (name, "host.example.com", port, false, model, false, false,
        false, "");
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
      SiteForm site = new SiteForm ("prod", "mvs.example.com", 992, true, 4, true, true, true,
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
      SiteForm site = site ("", 23, 3);

      assertEquals ("", site.port.getText (), "o widget fica em branco");
      assertEquals (23, site.getPort (), "mas a leitura ainda devolve 23");
    }

    @Test
    @DisplayName ("com nome vazio, o modelo 2 fica em branco no widget")
    void blankModelForDefaultOnUnnamedSite ()
    {
      SiteForm site = site ("", 992, 2);

      assertEquals ("", site.model.getText (), "o widget fica em branco");
      assertEquals (2, site.getModel (), "mas a leitura ainda devolve 2");
    }

    @Test
    @DisplayName ("com nome preenchido, os defaults aparecem no widget")
    void namedSiteKeepsDefaultsVisible ()
    {
      SiteForm site = site ("prod", 23, 2);

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
      SiteForm site = site ("prod", 992, 2);

      assertEquals (992, site.getPort ());
      assertEquals ("992", site.port.getText ());
    }

    @ParameterizedTest (name = "porta \"{0}\"")
    @DisplayName ("valor invalido vira 23 e o widget e reescrito")
    @ValueSource (strings = { "0", "-1", "abc", "", "  ", "99.5" })
    void invalidPortIsRewritten (String raw)
    {
      SiteForm site = site ("prod", 992, 2);
      site.port.setText (raw);

      assertEquals (23, site.getPort (), "a leitura devolve o default");
      assertEquals ("23", site.port.getText (), "e o widget foi reescrito pelo getter");
    }

    @Test
    @DisplayName ("ler duas vezes produz efeitos diferentes")
    void readingTwiceDiffers ()
    {
      SiteForm site = site ("prod", 992, 2);
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
      SiteForm site = site ("prod", 992, model);

      assertEquals (model, site.getModel ());
      assertEquals (String.valueOf (model), site.model.getText ());
    }

    @ParameterizedTest (name = "modelo \"{0}\"")
    @DisplayName ("fora da faixa vira 2 e o widget e reescrito")
    @ValueSource (strings = { "1", "0", "-3", "6", "99", "abc", "" })
    void invalidModelIsRewritten (String raw)
    {
      SiteForm site = site ("prod", 992, 3);
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
      SiteForm site = site ("prod", 992, 2);

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
      SiteForm site = site ("prod", 992, 2);

      assertSame (site.extended, site.getCheckBoxField (3));
      assertSame (site.plugins, site.getCheckBoxField (5));
      assertSame (site.ssl, site.getCheckBoxField (6));
      assertSame (site.trustAll, site.getCheckBoxField (7));
    }

    @Test
    @DisplayName ("cada posicao existe em exatamente um dos dois arrays")
    void positionsAreComplementary ()
    {
      SiteForm site = site ("prod", 992, 2);

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
      SiteForm site = new SiteForm ("prod", "mvs.example.com", 992, false, 2, false,
          false, false, "/tmp/dm3270");

      assertEquals ("Site [name=prod, url=mvs.example.com, port=992, folder=/tmp/dm3270]",
          site.toString ());
    }

    @Test
    @DisplayName ("imprimir um site com porta invalida altera o site")
    void printingMutatesTheSite ()
    {
      SiteForm site = site ("prod", 992, 2);
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
      SiteForm site = site ("prod", 992, 2);

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
      SiteForm site = site ("prod", 992, 2);

      assertNotNull (site.name);
      site.name.setText ("outro");

      assertEquals ("outro", site.getName (), "qualquer chamador pode reescrever o site");
    }

    @Test
    @DisplayName ("posicoes fora dos arrays paralelos nao tem widget")
    void unusedPositionsAreNull ()
    {
      SiteForm site = site ("prod", 992, 2);

      assertNull (site.getTextField (3));
      assertNull (site.getCheckBoxField (0));
    }
  }

  /*
   * A propriedade que o split do SiteForm precisa preservar, e que nenhum teste cobria.
   *
   * Os widgets sao LIVE: quem recebe um SiteForm nao recebe uma copia dos valores, recebe o
   * proprio campo de texto. O TransferManager guarda o SiteForm no construtor e so chama
   * getFolder () quando um comando IND$FILE aparece; o TransferMenu chama
   * FileSaver.getHomePath (site) no instante em que o usuario aciona o menu. Se o SiteForm
   * virasse um valor imutavel tirado no getSelectedSite (), as duas leituras passariam a
   * devolver o valor de um instante anterior.
   *
   * O mesmo vale para a correcao silenciosa, e ai a consequencia e persistente: getPort ()
   * reescreve o widget, e SiteListStage.savePrefs le site.port.getText () depois, gravando
   * nas Preferences o valor JA corrigido. Um valor congelado antes da correcao mudaria o
   * que fica gravado no disco.
   *
   * Estes quatro testes congelam a cadeia inteira. Sao a rede que autoriza o split.
   */
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("os consumidores leem o widget no momento do uso, nao no da entrega")
  class LateReading
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("quem guardou o Site enxerga a edicao posterior da pasta")
    void consumerSeesLaterFolderEdit ()
    {
      SiteForm site = site ("prod", 992, 2);
      Path before = FileSaver.getHomePath (site);

      site.folder.setText ("outra-pasta");
      Path after = FileSaver.getHomePath (site);

      assertNotEquals (before, after, "a leitura acontece na chamada, nao na entrega");
      assertTrue (after.endsWith ("outra-pasta"));
    }

    @Test
    @DisplayName ("quem guardou o Site enxerga a edicao posterior da porta")
    void consumerSeesLaterPortEdit ()
    {
      SiteForm site = site ("prod", 992, 2);
      assertEquals (992, site.getPort ());

      site.port.setText ("1023");

      assertEquals (1023, site.getPort (), "o valor novo, e nao o do momento da entrega");
    }

    @Test
    @DisplayName ("a correcao de getPort fica visivel para quem ler o widget depois")
    void portCorrectionReachesTheLaterWidgetRead ()
    {
      SiteForm site = site ("prod", 992, 2);
      site.port.setText ("abc");

      site.getPort ();

      // e exatamente este texto que SiteListStage.savePrefs grava nas Preferences
      assertEquals ("23", site.port.getText ());
    }

    @Test
    @DisplayName ("a correcao de getModel fica visivel para quem ler o widget depois")
    void modelCorrectionReachesTheLaterWidgetRead ()
    {
      SiteForm site = site ("prod", 992, 3);
      site.model.setText ("9");

      site.getModel ();

      assertEquals ("2", site.model.getText ());
    }
  }
}
