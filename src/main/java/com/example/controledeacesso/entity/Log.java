package com.example.controledeacesso.entity;

import com.example.controledeacesso.entity.Enum.TipoAcesso;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Tabela "logs" no Cloudflare D1 (colunas: id, pessoa_cpf, tipo, data_hora, catraca_id).
 * catracaNome/catracaLocal vem do JOIN com a tabela catraca; ficam nulos nos logs antigos.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Log {

   private Long id;

   private Pessoa pessoa;

   private TipoAcesso tipoAcesso;
   private LocalDateTime dataHora;

   private Long catracaId;
   private String catracaNome;
   private String catracaLocal;

   public Log(Long id, Pessoa pessoa, TipoAcesso tipoAcesso, LocalDateTime dataHora) {
      this(id, pessoa, tipoAcesso, dataHora, null, null, null);
   }
}
