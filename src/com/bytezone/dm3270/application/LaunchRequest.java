package com.bytezone.dm3270.application;

import java.util.Optional;

import com.bytezone.dm3270.runtime.TerminalFunction;
import com.bytezone.dm3270.utilities.Site;

// Mantem referencias vivas a Site e strings cruas de replay para nao antecipar
// validacoes nem efeitos colaterais de SiteForm fora do ramo selecionado.
// -----------------------------------------------------------------------------------//
record LaunchRequest (TerminalFunction function, Optional<Site> serverSite,
    Optional<Site> clientSite, String spyFolder, String replayFile)
// -----------------------------------------------------------------------------------//
{
}
