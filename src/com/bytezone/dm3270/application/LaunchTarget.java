package com.bytezone.dm3270.application;

import java.nio.file.Path;
import java.util.Optional;

import com.bytezone.dm3270.runtime.TerminalFunction;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.session.Session;
import com.bytezone.dm3270.utilities.Site;

// Porta de efeitos colaterais do lancamento, mantendo LaunchCoordinator sem JavaFX.
// -----------------------------------------------------------------------------------//
interface LaunchTarget
// -----------------------------------------------------------------------------------//
{
  void hideOptions ();

  void showOptions ();

  LaunchRequest launchRequest ();

  // Devolve true quando o usuario confirma o alerta e a janela de opcoes deve reabrir.
  boolean alert (String message);

  // Consulta tardia usada pelo replay apos carregar a sessao e descobrir o nome do servidor.
  Optional<Site> findServerSite (String siteName);

  // Configura o modelo do site e guarda as dimensoes alternativas entre lancamentos.
  void applyModel (Site serverSite);

  void useAlternateScreenDimensions (ScreenDimensions screenDimensions);

  // serverSite pode ser null no replay quando o servidor da sessao nao e encontrado.
  void showConsole (TerminalFunction function, Site serverSite);

  void connectConsole ();

  // Deve ser chamado apos showConsole(), pois usa a tela recem-construida.
  void showReplay (Session session, Path path);

  // Os dois Sites sao exibidos no SpyPane; a Screen nasce com site null (sem banco).
  void showSpy (TerminalFunction function, Site serverSite, Site clientSite);

  void showMainframe (int port);
}
