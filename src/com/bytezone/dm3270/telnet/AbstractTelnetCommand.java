package com.bytezone.dm3270.telnet;

import com.bytezone.dm3270.buffers.AbstractReplyBuffer;
import com.bytezone.dm3270.streams.TelnetState;

/*
 * A base dos dois comandos telnet: guarda o estado da negociacao e entrega os proprios bytes
 * crus, sem expandir 0xFF nem acrescentar EOR.
 *
 * ESTA CLASSE MORAVA EM buffers, e era a unica aresta daquele pacote para streams. buffers e
 * a abstracao mais baixa do sistema - um bloco de bytes vindo do socket -, e o pacote inteiro
 * so nomeia tres coisas de fora; que uma delas fosse o estado de uma sessao telnet era
 * acidente historico, nao camada.
 *
 * O campo telnetState nunca foi usado AQUI DENTRO: quem o le sao as subclasses, e as
 * subclasses que existem sao duas, TelnetCommand e TelnetSubcommand, as duas neste pacote.
 * Trazer a classe para junto de quem a estende tira a dependencia de buffers de verdade -
 * depois disto buffers nao conhece mais streams em ponto nenhum - e nao e troca de rotulo
 * para agradar o placar, que e o que a regra 5 desta refatoracao proibe.
 *
 * Continua estendendo buffers.AbstractReplyBuffer: telnet -> buffers ja existia e nao muda.
 */
// -----------------------------------------------------------------------------------//
public abstract class AbstractTelnetCommand extends AbstractReplyBuffer
// -----------------------------------------------------------------------------------//
{
  protected TelnetState telnetState;

  public AbstractTelnetCommand (byte[] buffer, int offset, int length,
      TelnetState telnetState)
  {
    super (buffer, offset, length);
    this.telnetState = telnetState;
  }

  @Override
  public byte[] getTelnetData ()
  {
    return data;        // do not expand anything, do not append EOR bytes
  }
}
