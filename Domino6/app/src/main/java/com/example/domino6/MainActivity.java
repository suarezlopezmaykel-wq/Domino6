package com.example.domino6;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    static final int REQ_BT=40;
    static final UUID SPP_UUID=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    BluetoothAdapter adapter; NetworkHost host; NetworkClient client;

    @Override public void onCreate(Bundle b){super.onCreate(b); adapter=BluetoothAdapter.getDefaultAdapter(); requestBt(); showMenu();}
    void requestBt(){ if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},REQ_BT); }
    void showMenu(){
        LinearLayout l=base(); l.addView(title("DOMINÓ 6")); l.addView(txt("Doble-seis · 28 fichas · sin Internet"));
        Button cpu=btn("Jugar contra la computadora"); l.addView(cpu); cpu.setOnClickListener(v->{new LocalGame(2,true,0).start();});
        Button bt=btn("Jugar por Bluetooth"); l.addView(bt); bt.setOnClickListener(v->bluetoothMenu());
        Button rules=btn("Reglas"); l.addView(rules); rules.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Reglas de Dominó 6").setMessage("Se usan 28 fichas doble-seis. Cada jugador recibe 7 fichas. Se juega haciendo coincidir uno de los extremos. Si puedes jugar, debes jugar. Si no puedes, pasas. Gana la mano quien se queda sin fichas. Si todos pasan consecutivamente y nadie puede jugar, hay tranca: gana quien tenga MENOS puntos sumando los dos números de sus fichas restantes.").setPositiveButton("OK",null).show());
        setContentView(l);
    }
    void bluetoothMenu(){
        requestBt(); if(adapter==null){toast("Este dispositivo no tiene Bluetooth.");return;}
        String[] opts={"Crear partida (anfitrión)","Unirse a partida"};
        new AlertDialog.Builder(this).setTitle("Bluetooth").setItems(opts,(d,w)->{if(w==0)chooseHostPlayers();else joinBluetooth();}).setNegativeButton("Cancelar",null).show();
    }
    void chooseHostPlayers(){String[] p={"2 jugadores","3 jugadores","4 jugadores"};new AlertDialog.Builder(this).setTitle("¿Cuántos jugadores?").setItems(p,(d,w)->startHost(w+2)).setNegativeButton("Cancelar",null).show();}
    void startHost(int n){
        try{ if(!adapter.isEnabled()){startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),7); new Handler().postDelayed(()->startHostServer(n),900);} else startHostServer(n); }catch(Exception e){toast("No se pudo activar Bluetooth");}
    }
    void startHostServer(int n){
        host=new NetworkHost(n); host.start();
        new AlertDialog.Builder(this).setTitle("Sala creada").setMessage("Jugadores: "+n+"\n\nAhora los demás deben emparejarse con este teléfono y elegirlo en 'Unirse a partida'.\n\nLa partida comienza automáticamente cuando estén conectados todos.").setPositiveButton("Esperar",null).setNegativeButton("Cancelar",(d,w)->{host.stop();showMenu();}).show();
    }
    void joinBluetooth(){
        if(adapter==null){toast("Bluetooth no disponible");return;}
        try{Set<BluetoothDevice> ds=adapter.getBondedDevices(); final ArrayList<BluetoothDevice> list=new ArrayList<>(ds); if(list.isEmpty()){toast("Primero empareja los teléfonos desde Ajustes > Bluetooth.");return;} String[] names=new String[list.size()];for(int i=0;i<list.size();i++)names[i]=safeName(list.get(i));
            new AlertDialog.Builder(this).setTitle("Dispositivos emparejados").setItems(names,(d,w)->connectToHost(list.get(w))).setNegativeButton("Cancelar",null).show();
        }catch(SecurityException e){requestBt();}
    }
    String safeName(BluetoothDevice d){try{return d.getName()+"\n"+d.getAddress();}catch(Exception e){return "Dispositivo";}}
    void connectToHost(BluetoothDevice dev){client=new NetworkClient(dev); client.start();}

    LinearLayout base(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(24,50,24,24);l.setGravity(Gravity.CENTER_HORIZONTAL);return l;}
    TextView title(String s){TextView v=txt(s);v.setTextSize(30);v.setGravity(Gravity.CENTER);v.setPadding(0,0,0,24);return v;}
    TextView txt(String s){TextView v=new TextView(this);v.setText(s);v.setTextSize(18);v.setPadding(0,12,0,12);return v;}
    Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextSize(16);b.setAllCaps(false);b.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));return b;}
    void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show());}

    // ---------- Motor local: jugador vs computadora ----------
    class LocalGame extends LinearLayout {
        ArrayList<ArrayList<Domino>> hands=new ArrayList<>(); ArrayList<Domino> chain=new ArrayList<>();
        int turn=0,left,right,passCount=0; boolean first=true; boolean cpu;
        LinearLayout handBox; TextView status;
        LocalGame(int n,boolean cpu,int ignored){super(MainActivity.this);this.cpu=cpu;setOrientation(VERTICAL);setPadding(10,15,10,10);newGame();}
        void start(){setContentView(this);}
        void newGame(){hands.clear();ArrayList<Domino> deck=deck();Collections.shuffle(deck);for(int i=0;i<2;i++)hands.add(new ArrayList<>());for(int k=0;k<7;k++)for(int p=0;p<2;p++)hands.get(p).add(deck.remove(0));chain.clear();first=true;passCount=0;turn=0;draw();}
        ArrayList<Domino> deck(){ArrayList<Domino>d=new ArrayList<>();for(int a=0;a<=6;a++)for(int b=a;b<=6;b++)d.add(new Domino(a,b));return d;}
        void draw(){removeAllViews();addView(title("DOMINÓ 6"));status=txt(turn==0?"Tu turno":"Turno de la computadora");addView(status);addView(txt(chain.isEmpty()?"Mesa: vacía":"Mesa: "+chainString()));addView(txt("Tus fichas:"));handBox=new LinearLayout(MainActivity.this);handBox.setGravity(Gravity.CENTER);addView(handBox);for(int i=0;i<hands.get(0).size();i++){final int ix=i;Button b=btn(hands.get(0).get(i).toString());handBox.addView(b,new LinearLayout.LayoutParams(0,82,1));b.setOnClickListener(v->playHuman(ix));}Button pass=btn("Pasar");pass.setEnabled(host.game==null||!host.game.finished);addView(pass);pass.setOnClickListener(v->passHuman());Button menu=btn("Menú");addView(menu);menu.setOnClickListener(v->showMenu());if(turn==1)new Handler().postDelayed(this::cpuMove,450);}
        String chainString(){StringBuilder s=new StringBuilder();for(Domino d:chain)s.append(d).append(" ");return s.toString();}
        boolean valid(Domino d){return first||d.a==left||d.b==left||d.a==right||d.b==right;}
        void place(Domino d){if(first){chain.add(d);left=d.a;right=d.b;first=false;return;}if(d.a==left){chain.add(0,new Domino(d.b,d.a));left=d.b;}else if(d.b==left){chain.add(0,d);left=d.a;}else if(d.a==right){chain.add(d);right=d.b;}else if(d.b==right){chain.add(new Domino(d.b,d.a));right=d.a;}}
        boolean playable(int p){for(Domino d:hands.get(p))if(valid(d))return true;return false;}
        void playHuman(int i){if(turn!=0)return;Domino d=hands.get(0).get(i);if(!valid(d)){toast("Esa ficha no puede colocarse.");return;}place(d);hands.get(0).remove(i);passCount=0;if(hands.get(0).isEmpty()){finish("¡Has ganado la mano!");return;}turn=1;draw();}
        void passHuman(){if(turn!=0)return;if(playable(0)){toast("Tienes una ficha que puedes jugar.");return;}passCount++;if(passCount>=2){tranca();return;}turn=1;draw();}
        void cpuMove(){if(playable(1)){for(int i=0;i<hands.get(1).size();i++){if(valid(hands.get(1).get(i))){Domino d=hands.get(1).remove(i);place(d);passCount=0;if(hands.get(1).isEmpty()){finish("La computadora ganó la mano.");return;}turn=0;draw();return;}}}passCount++;if(passCount>=2){tranca();return;}turn=0;draw();}
        void tranca(){int a=points(hands.get(0)),b=points(hands.get(1));String s="PARTIDA TRANCADA\n\nTú: "+a+" puntos\nComputadora: "+b+" puntos\n\n"+(a<b?"🏆 Ganas tú":b<a?"🏆 Gana la computadora":"Empate: misma cantidad de puntos");finish(s);}
        int points(ArrayList<Domino> h){int x=0;for(Domino d:h)x+=d.a+d.b;return x;}
        void finish(String s){new AlertDialog.Builder(MainActivity.this).setTitle("Fin de la mano").setMessage(s).setPositiveButton("Nueva mano",(d,w)->newGame()).setNegativeButton("Menú",(d,w)->showMenu()).setCancelable(false).show();}
    }

    // ---------- Bluetooth host: autoridad de la partida ----------
    class NetworkHost {
        int n; BluetoothServerSocket server; ArrayList<Peer> peers=new ArrayList<>(); GameState game; boolean running;
        NetworkHost(int n){this.n=n;}
        void start(){running=true;new Thread(()->{try{server=adapter.listenUsingRfcommWithServiceRecord("Domino6",SPP_UUID);while(running && peers.size()<n-1){BluetoothSocket s=server.accept();Peer p=new Peer(s,peers.size()+1);peers.add(p);p.start();}if(peers.size()==n-1){game=new GameState(n);runOnUiThread(()->new HostGameView().show());broadcast();}}catch(Exception e){toast("Sala Bluetooth cerrada: "+e.getMessage());}}).start();}
        void stop(){running=false;try{if(server!=null)server.close();}catch(Exception ignored){}for(Peer p:peers)p.close();}
        void broadcast(){if(game==null)return;String msg=game.stateFor(0);for(Peer p:peers)p.send(game.stateFor(p.id));runOnUiThread(()->{if(game!=null)new HostGameView().show();});}
        void action(int player,String cmd){if(game==null||game.finished||game.turn!=player)return;if(cmd.startsWith("PLAY|")){int ix=parse(cmd.substring(5));if(ix>=0&&ix<game.hands.get(player).size()&&game.valid(game.hands.get(player).get(ix))){game.play(player,ix);broadcast();}}else if(cmd.equals("PASS")){if(!game.playable(player)){game.pass();broadcast();}}}
        int parse(String s){try{return Integer.parseInt(s);}catch(Exception e){return -1;}}
        class Peer {BluetoothSocket socket;BufferedReader in;PrintWriter out;int id;Peer(BluetoothSocket s,int id)throws IOException{socket=s;this.id=id;in=new BufferedReader(new InputStreamReader(s.getInputStream()));out=new PrintWriter(new BufferedWriter(new OutputStreamWriter(s.getOutputStream())),true);}void start(){new Thread(()->{try{send("HELLO|"+id+"|"+n);String line;while((line=in.readLine())!=null)action(id,line);}catch(Exception ignored){}finally{close();}}).start();}void send(String s){try{out.println(s);}catch(Exception ignored){}}void close(){try{socket.close();}catch(Exception ignored){}}}
    }

    class GameState {
        int n,turn=0,left,right,passCount=0;boolean first=true,finished=false;int winner=-1;String result="";ArrayList<ArrayList<Domino>> hands=new ArrayList<>();ArrayList<Domino> chain=new ArrayList<>();
        GameState(int n){this.n=n;ArrayList<Domino>d=new ArrayList<>();for(int a=0;a<=6;a++)for(int b=a;b<=6;b++)d.add(new Domino(a,b));Collections.shuffle(d);for(int i=0;i<n;i++)hands.add(new ArrayList<>());for(int k=0;k<7;k++)for(int p=0;p<n;p++)hands.get(p).add(d.remove(0));}
        boolean valid(Domino d){return first||d.a==left||d.b==left||d.a==right||d.b==right;}
        boolean playable(int p){for(Domino d:hands.get(p))if(valid(d))return true;return false;}
        void place(Domino d){if(first){chain.add(d);left=d.a;right=d.b;first=false;}else if(d.a==left){chain.add(0,new Domino(d.b,d.a));left=d.b;}else if(d.b==left){chain.add(0,d);left=d.a;}else if(d.a==right){chain.add(d);right=d.b;}else if(d.b==right){chain.add(new Domino(d.b,d.a));right=d.a;}}
        void play(int p,int ix){Domino d=hands.get(p).remove(ix);place(d);passCount=0;if(hands.get(p).isEmpty()){finished=true;winner=p;result="🏆 Gana Jugador "+(p+1)+" porque se quedó sin fichas.";return;}turn=(turn+1)%n;}
        void pass(){passCount++;if(passCount>=n){finished=true;result=trancaText();return;}turn=(turn+1)%n;}
        String stateFor(int p){StringBuilder h=new StringBuilder();for(int i=0;i<hands.get(p).size();i++){if(i>0)h.append(';');h.append(hands.get(p).get(i).a).append(',').append(hands.get(p).get(i).b);}StringBuilder c=new StringBuilder();for(int i=0;i<chain.size();i++){if(i>0)c.append(';');c.append(chain.get(i).a).append(',').append(chain.get(i).b);}return "STATE|"+p+"|"+turn+"|"+(first?"1":"0")+"|"+left+"|"+right+"|"+passCount+"|"+(finished?"1":"0")+"|"+winner+"|"+result.replace("\n","~")+"|"+h+"|"+c;}
        int points(ArrayList<Domino> h){int x=0;for(Domino d:h)x+=d.a+d.b;return x;}
        String trancaText(){StringBuilder s=new StringBuilder("PARTIDA TRANCADA\n\n");int best=Integer.MAX_VALUE;ArrayList<Integer>winners=new ArrayList<>();for(int i=0;i<n;i++){int x=points(hands.get(i));s.append("Jugador ").append(i+1).append(": ").append(x).append(" puntos\n");if(x<best){best=x;winners.clear();winners.add(i);}else if(x==best){winners.add(i);}}s.append("\n");if(winners.size()==1)s.append("🏆 Gana Jugador ").append(winners.get(0)+1).append(" con ").append(best).append(" puntos.");else s.append("⚖ Empate entre jugadores con ").append(best).append(" puntos.");return s.toString();}
    }

    class HostGameView extends LinearLayout {
        TextView status,board;LinearLayout handBox;HostGameView(){super(MainActivity.this);setOrientation(VERTICAL);setPadding(10,15,10,10);}void show(){setContentView(this);draw();}
        void draw(){removeAllViews();addView(title("DOMINÓ 6 · Bluetooth"));addView(txt("Tú eres Jugador 1"));status=txt(gameStatus());addView(status);board=txt(host.game==null?"":host.game.chain.isEmpty()?"Mesa: vacía":"Mesa: "+chainText(host.game.chain));addView(board);addView(txt("Tus fichas:"));handBox=new LinearLayout(MainActivity.this);addView(handBox);if(host.game!=null)for(int i=0;i<host.game.hands.get(0).size();i++){final int ix=i;Button b=btn(host.game.hands.get(0).get(i).toString());handBox.addView(b,new LinearLayout.LayoutParams(0,82,1));b.setEnabled(!host.game.finished);b.setOnClickListener(v->host.action(0,"PLAY|"+ix));}Button pass=btn("Pasar");addView(pass);pass.setOnClickListener(v->host.action(0,"PASS"));Button menu=btn("Salir al menú");addView(menu);menu.setOnClickListener(v->{host.stop();showMenu();});}
        String gameStatus(){if(host.game==null)return"Esperando jugadores…";if(host.game.finished)return host.game.result;return host.game.turn==0?"Tu turno":"Turno del Jugador "+(host.game.turn+1);}
        String chainText(ArrayList<Domino> c){StringBuilder s=new StringBuilder();for(Domino d:c)s.append(d).append(' ');return s.toString();}
    }

    class NetworkClient {
        BluetoothDevice dev;BluetoothSocket socket;BufferedReader in;PrintWriter out;int me=-1,n=0;ArrayList<Domino> hand=new ArrayList<>(),chain=new ArrayList<>();int turn;boolean first;int left,right,passCount,finished,winner=-1;String result="";
        NetworkClient(BluetoothDevice d){dev=d;}
        void start(){new Thread(()->{try{socket=dev.createRfcommSocketToServiceRecord(SPP_UUID);socket.connect();in=new BufferedReader(new InputStreamReader(socket.getInputStream()));out=new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())),true);String line;while((line=in.readLine())!=null)parse(line);}catch(Exception e){toast("No se pudo conectar por Bluetooth: "+e.getMessage());}}).start();}
        void parse(String s){if(s.startsWith("HELLO|")){String[]a=s.split("\\|");me=Integer.parseInt(a[1]);n=Integer.parseInt(a[2]);runOnUiThread(()->toast("Conectado como Jugador "+(me+1)+" de "+n));}else if(s.startsWith("STATE|")){decode(s);runOnUiThread(()->drawClient());}}
        void decode(String s){String[]a=s.split("\\|",-1);turn=Integer.parseInt(a[2]);winner=Integer.parseInt(a[8]);result=a[9].replace("~","\n");first="1".equals(a[3]);left=Integer.parseInt(a[4]);right=Integer.parseInt(a[5]);passCount=Integer.parseInt(a[6]);finished="1".equals(a[7]);hand=parseDominoList(a.length>10?a[10]:"");chain=parseDominoList(a.length>11?a[11]:"");}
        ArrayList<Domino> parseDominoList(String s){ArrayList<Domino> r=new ArrayList<>();if(s==null||s.isEmpty())return r;for(String z:s.split(";")){{String[]q=z.split(",");if(q.length==2)r.add(new Domino(Integer.parseInt(q[0]),Integer.parseInt(q[1])));}}return r;}
        void send(String s){try{out.println(s);}catch(Exception ignored){}}
        void drawClient(){removeViewIfAny();LinearLayout l=base();l.addView(title("DOMINÓ 6 · Bluetooth"));l.addView(txt("Eres Jugador "+(me+1)+" de "+n));l.addView(txt(finished?result:(turn==me?"TU TURNO":"Turno del Jugador "+(turn+1))));l.addView(txt(chain.isEmpty()?"Mesa: vacía":"Mesa: "+chainText(chain)));l.addView(txt("Tus fichas:"));LinearLayout hb=new LinearLayout(MainActivity.this);l.addView(hb);for(int i=0;i<hand.size();i++){final int ix=i;Button b=btn(hand.get(i).toString());hb.addView(b,new LinearLayout.LayoutParams(0,82,1));b.setOnClickListener(v->{if(turn==me)send("PLAY|"+ix);});}Button pass=btn("Pasar");l.addView(pass);pass.setOnClickListener(v->{if(turn==me)send("PASS");});Button menu=btn("Menú");l.addView(menu);menu.setOnClickListener(v->{try{socket.close();}catch(Exception ignored){}showMenu();});setContentView(l);}
        void removeViewIfAny(){}
        String chainText(ArrayList<Domino> c){StringBuilder s=new StringBuilder();for(Domino d:c)s.append(d).append(' ');return s.toString();}
    }
    static class Domino implements Serializable {int a,b;Domino(int a,int b){this.a=a;this.b=b;}public String toString(){return "["+a+"|"+b+"]";}}
}
