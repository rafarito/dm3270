package com.bytezone.dm3270.display;

// O construtor de FontManagerType1 chama fontChanged de volta; implementacoes devem tolerar
// chamada antes da inicializacao completa (adjustStage e false ao redimensionar a janela).
// -----------------------------------------------------------------------------------//
@FunctionalInterface
interface FontChangeTarget
// -----------------------------------------------------------------------------------//
{
  void fontChanged (FontDetails fontDetails, boolean adjustStage);
}
