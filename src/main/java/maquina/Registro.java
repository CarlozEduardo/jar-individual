package maquina;

import com.github.britooo.looca.api.core.Looca;
import com.github.britooo.looca.api.group.rede.RedeInterface;
import conexao.Conexao;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLOutput;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class Registro {
    Conexao conectar = new Conexao();
    JdbcTemplate conec = conectar.getConexao();
    Looca looca = new Looca();
    private Timer permanenciaDeDados;
    private Double consumoCPU;
    private Double consumoRam;
    private Double consumoDisco;
    private String internet;
    private Integer velocidadeMpbs;

    //Construtor
    public Registro() {
        Double disco = 0.0;
        for (int i = 0; i < looca.getGrupoDeDiscos().getVolumes().size(); i++) {
            disco += Conversor.converterDoubleTresDecimais(Conversor.formatarBytes(looca.getGrupoDeDiscos().getVolumes().get(i).getTotal() - looca.getGrupoDeDiscos().getVolumes().get(i).getDisponivel()));
        }

        List<RedeInterface> listaRede = looca.getRede().getGrupoDeInterfaces().getInterfaces().stream().filter(redeInterface -> !redeInterface.getEnderecoIpv4().isEmpty()).toList();

        this.permanenciaDeDados = new Timer();
        this.consumoCPU = Conversor.converterDoubleDoisDecimais(looca.getProcessador().getUso());
        this.consumoRam = Conversor.converterDoubleTresDecimais(Conversor.formatarBytes(looca.getMemoria().getEmUso()));
        this.consumoDisco = disco;

        Boolean velocidadeEstavel = listaRede.get(0).getBytesRecebidos().intValue() > 10000000 ? true : false;
        this.internet = velocidadeEstavel ? "Sua internet está estável" : "Sua internet não está estável";

        this.velocidadeMpbs = listaRede.get(0).getBytesRecebidos().intValue() / 1000000;
    }

    public void inserirRegistros(Integer idMaquina, Limite trigger) {
        try {
            // Loop para registrar o dados do recurso ao banco
                permanenciaDeDados.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Date data = new Date(); // data da coleta
                        // insert ao banco
                        conec.update("""
                                INSERT INTO registro(dataHora, fkMaquina, consumoCpu, consumoRam, consumoDisco) VALUES (?, ?, ?, ?, ?)
                                """, new Timestamp(data.getTime()), idMaquina, getConsumoCPU(),getConsumoRam(), getConsumoDisco()
                                );

                        System.out.println("""
                    *------------------------------------*
                    |           Dados Coletados          |
                    *------------------------------------*
                    |Consumo da CPU: %.2f
                    |Consumo da RAM: %.2f
                    |Consumo da Disco: %.2f
                    |Internet: %s
                    |Velocidade em Mpbs: %d 
                    *------------------------------------*
                                """.formatted(getConsumoCPU(), getConsumoRam(),getConsumoDisco(), getInternet(), getVelocidadeMpbs()));
                        triggerRegistro(idMaquina, trigger); // Pos inserção de registro dos volateis
                    }
                },0, 50000);
        } catch (EmptyResultDataAccessException e) {
            System.out.println("Não foi encontrada nenhuma máquina vinculada a este usuário");
        }
    }

    private void triggerRegistro(Integer idMaquina, Limite trigger) {
        try {
                // Comparando limite com o consumo
                if ( trigger.getLimiteCPU() < consumoCPU ||
                        trigger.getLimiteRam() < consumoRam ||
                        trigger.getLimiteDisco() < consumoDisco
                ) {
                    // Caso consumo ultrapasse o limite
                    Date data = new Date(); // Data e hora do alerta
                    // Coletando id do registro mais recente
                    Integer fkRegistro = conec.queryForObject("SELECT idRegistro FROM registro WHERE fkMaquina = ? ORDER BY idRegistro DESC LIMIT 1", Integer.class, idMaquina);

                    // Insert do alerta
                    conec.update("INSERT INTO alerta(dataAlerta, fkRegistro) VALUES (?, ?)", new Timestamp(data.getTime()), fkRegistro);
                }
        } catch (EmptyResultDataAccessException e) {
            System.out.println("Erro no insert do trigger");
        }
    }

    public Double getConsumoCPU() {
        return consumoCPU;
    }

    public Double getConsumoRam() {
        return consumoRam;
    }

    public Double getConsumoDisco() {
        return consumoDisco;
    }

    public String getInternet() {
        return internet;
    }

    public Integer getVelocidadeMpbs() {
        return velocidadeMpbs;
    }

    @Override
    public String toString() {
        return """
                Registro
                Consumo de CPU: %.2f
                Consumo de RAM: %.2f
                Consumo de Disco: %.2f
                """.formatted(consumoCPU, consumoRam, consumoDisco);
    }
}
