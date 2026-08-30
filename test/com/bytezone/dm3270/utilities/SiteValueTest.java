package com.bytezone.dm3270.utilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/*
 * O SiteValue e a implementacao headless da porta Site.
 *
 * REPARE NO QUE ESTA CLASSE DE TESTE NAO TEM: nao ha @ExtendWith (JavaFxToolkit.class). E
 * essa ausencia que e o teste principal. O SiteFormTest precisa do toolkit para existir,
 * porque instanciar um TextField sem ele lanca excecao; aqui nada disso e necessario, e e
 * exatamente essa a propriedade que a interface Site foi criada para permitir. Se alguem
 * puser um widget dentro do SiteValue, esta classe para de compilar ou passa a falhar, e a
 * regra screenModelDoesNotKnowJavaFx tem uma irma vigiando o mesmo no ArchUnit.
 *
 * O toString e verificado contra o formato exato do SiteForm porque os dois precisam ser
 * intercambiaveis: quem recebe um Site nao sabe qual das duas implementacoes tem em maos.
 */
// -----------------------------------------------------------------------------------//
@DisplayName ("SiteValue - a configuracao de conexao sem widget nenhum")
class SiteValueTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  private static SiteValue site ()
  // ---------------------------------------------------------------------------------//
  {
    return new SiteValue ("prod", "mvs.example.com", 992, true, 4, true, true, true,
        "/tmp/dm3270");
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("acessores")
  class Accessors
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("devolvem o que o construtor recebeu")
    void returnWhatWasGiven ()
    {
      Site site = site ();

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
    @DisplayName ("nao tem efeito colateral: ler duas vezes da o mesmo resultado")
    void readingTwiceIsIdentical ()
    {
      Site site = new SiteValue ("prod", "host", 0, false, 9, false, false, false, "");

      // o SiteForm corrigiria porta 0 para 23 e modelo 9 para 2, reescrevendo o widget.
      // aqui nao ha widget para corrigir, e o valor informado sai como entrou.
      assertEquals (0, site.getPort ());
      assertEquals (0, site.getPort ());
      assertEquals (9, site.getModel ());
      assertEquals (9, site.getModel ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("valor")
  class Value
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("dois com os mesmos campos sao iguais")
    void equalsByComponents ()
    {
      assertEquals (site (), site ());
      assertEquals (site ().hashCode (), site ().hashCode ());
    }

    @Test
    @DisplayName ("um campo diferente basta para diferenciar")
    void differsByOneComponent ()
    {
      SiteValue other = new SiteValue ("prod", "mvs.example.com", 23, true, 4, true, true,
          true, "/tmp/dm3270");

      assertNotEquals (site (), other);
    }

    @Test
    @DisplayName ("o toString repete o formato do SiteForm, e nao o do record")
    void toStringMatchesTheFormLayout ()
    {
      assertEquals ("Site [name=prod, url=mvs.example.com, port=992, folder=/tmp/dm3270]",
          site ().toString ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("o destino fixo do modo Test")
  class DefaultMainframe
  // ---------------------------------------------------------------------------------//
  {
    /*
     * Os mesmos argumentos de Console.DEFAULT_MAINFRAME. Quando ele era um SiteForm, o
     * construtor gravava "5555" e "2" nos widgets e os getters os liam de volta como
     * numero; o SiteValue devolve os inteiros direto. Este teste registra que o resultado
     * observavel e o mesmo, que e o que autoriza a troca.
     */
    @Test
    @DisplayName ("devolve os mesmos valores que o formulario devolvia")
    void matchesWhatTheFormProduced ()
    {
      Site mainframe =
          new SiteValue ("mainframe", "localhost", 5555, true, 2, false, false, false, "");

      assertEquals ("mainframe", mainframe.getName ());
      assertEquals ("localhost", mainframe.getURL ());
      assertEquals (5555, mainframe.getPort ());
      assertTrue (mainframe.getExtended ());
      assertEquals (2, mainframe.getModel ());
      assertFalse (mainframe.getPlugins ());
      assertFalse (mainframe.getSsl ());
      assertFalse (mainframe.getTrustAll ());
      assertEquals ("", mainframe.getFolder ());
    }
  }
}
