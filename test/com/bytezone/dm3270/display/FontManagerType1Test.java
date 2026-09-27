package com.bytezone.dm3270.display;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.bytezone.dm3270.testing.JavaFxToolkit;

/*
 * A PRIMEIRA REDE DO FontManagerType1, e ela so existe porque a porta FontChangeTarget existe.
 *
 * Antes do passo 13 o gerenciador de fonte recebia a Screen concreta, e a reentrancia - o
 * construtor dele chamando a tela de volta, de dentro do construtor DELA - so era observavel
 * indiretamente, pelo tamanho de fonte que chegava ao acessor (ScreenConstructionTest). Contra
 * um alvo que grava, a chamada e afirmada diretamente: quantas vezes, com que fonte, e com que
 * valor de adjustStage.
 *
 * Precisa do toolkit: Font.font e o Text que o FontDetails mede sao do JavaFX.
 *
 * "Monospaced" e fonte LOGICA do JavaFX - existe em toda plataforma. Por isso todo caso a usa:
 * um nome fisico passaria aqui e quebraria no CI.
 */
// -----------------------------------------------------------------------------------//
@ExtendWith (JavaFxToolkit.class)
@DisplayName ("FontManagerType1 - a fonte, e como ela chama a tela de volta")
class FontManagerType1Test
// -----------------------------------------------------------------------------------//
{
  private final List<String> calls = new ArrayList<> ();
  private final FontChangeTarget target = (details, adjustStage) -> calls
      .add (String.format ("fontChanged(%s, %d, %s)", details.name, (int) details.size,
          adjustStage));

  private Preferences prefs;

  // ---------------------------------------------------------------------------------//
  @BeforeEach
  void createPreferences ()
  // ---------------------------------------------------------------------------------//
  {
    prefs = Preferences.userRoot ().node ("dm3270-test/" + UUID.randomUUID ());
  }

  // ---------------------------------------------------------------------------------//
  @AfterEach
  void removePreferences () throws Exception
  // ---------------------------------------------------------------------------------//
  {
    prefs.removeNode ();
    prefs.flush ();
  }

  // ---------------------------------------------------------------------------------//
  private FontManagerType1 manager (int size)
  // ---------------------------------------------------------------------------------//
  {
    prefs.put ("FontName", "Monospaced");
    prefs.put ("FontSize", "" + size);
    return JavaFxToolkit.onFxThread ( () -> new FontManagerType1 (target, prefs));
  }

  // ---------------------------------------------------------------------------------//
  private void onFx (Runnable action)
  // ---------------------------------------------------------------------------------//
  {
    JavaFxToolkit.onFxThread ( () ->
    {
      action.run ();
      return null;
    });
  }

  /*
   * A REENTRANCIA, afirmada. Uma chamada so, com adjustStage verdadeiro, e ANTES de o
   * construtor devolver - a lista ja esta preenchida quando onFxThread retorna o objeto, e o
   * alvo nao teve outra oportunidade de ser chamado.
   */
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("o construtor chama a tela de volta UMA vez, com a fonte das preferencias")
  void theConstructorCallsBackOnce ()
  // ---------------------------------------------------------------------------------//
  {
    manager (20);

    assertEquals (List.of ("fontChanged(Monospaced, 20, true)"), calls);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("sem preferencias, Monospaced 16")
  void defaults ()
  // ---------------------------------------------------------------------------------//
  {
    JavaFxToolkit.onFxThread ( () -> new FontManagerType1 (target, prefs));

    assertEquals (List.of ("fontChanged(Monospaced, 16, true)"), calls);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("maior sobe um degrau da tabela de tamanhos e avisa com adjustStage")
  void bigger ()
  // ---------------------------------------------------------------------------------//
  {
    FontManagerType1 manager = manager (20);
    calls.clear ();

    onFx (manager::bigger);

    assertEquals (List.of ("fontChanged(Monospaced, 22, true)"), calls);
    assertEquals (22, manager.getFontSize ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("no maior tamanho, maior nao faz nada")
  void biggerAtTheTop ()
  // ---------------------------------------------------------------------------------//
  {
    FontManagerType1 manager = manager (22);
    calls.clear ();

    onFx (manager::bigger);

    assertEquals (List.of (), calls);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("menor desce um degrau - de 16 para 15, e nao para 14")
  void smaller ()
  // ---------------------------------------------------------------------------------//
  {
    FontManagerType1 manager = manager (16);
    calls.clear ();

    onFx (manager::smaller);

    assertEquals (List.of ("fontChanged(Monospaced, 15, true)"), calls);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("no menor tamanho, menor nao faz nada")
  void smallerAtTheBottom ()
  // ---------------------------------------------------------------------------------//
  {
    FontManagerType1 manager = manager (9);
    calls.clear ();

    onFx (manager::smaller);

    assertEquals (List.of (), calls);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("ajustar a janela avisa SEM adjustStage, e repetir a mesma medida nao avisa")
  void setFontToFit ()
  // ---------------------------------------------------------------------------------//
  {
    FontManagerType1 manager = manager (16);
    calls.clear ();

    onFx ( () -> manager.setFontToFit (30, 60));
    onFx ( () -> manager.setFontToFit (30, 60));

    assertEquals (1, calls.size (), calls.toString ());
    assertEquals (true, calls.get (0).endsWith (", false)"), calls.get (0));
  }
}
