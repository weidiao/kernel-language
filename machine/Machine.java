import java.io.*;
import java.util.*;

class Edge {
	Variable var;
	String name;

	Edge(String name, Variable var) {
		this.var = var;
		this.name = name;
	}
}

class Cell {
	int pointer;
	String name;

	Cell(String name, int pointer) {
		this.pointer = pointer;
		this.name = name;
	}
}

class Variable {
	String name;
	Box type;
	Edge[] edges;
	Cell[] cells;

	Variable(Box type, String name) {
		this.name = name;
		this.type = type;
		cells = new Cell[type.bytes.size()];
		edges = new Edge[type.fields.size()];
		for (int i = 0; i < cells.length; i++)
			cells[i] = new Cell(type.bytes.get(i), Memory.getOne());
		for (int i = 0; i < edges.length; i++)
			edges[i] = new Edge(type.fields.get(i).name, null);
	}

	Cell getCell(String s) {
		for (Cell c : cells)
			if (c.name.equals(s))
				return c;
		return null;
	}

	Edge getEdge(String s) {
		for (Edge e : edges)
			if (e.name.equals(s))
				return e;
		return null;
	}

	Fun getFun(String s) {
		for (Fun f : type.funs)
			if (f.name.equals(s))
				return f;
		return null;
	}
}

class Fun {
	Box type;
	String name;
	Field[] args;
	String[] body;

	Fun(Box type, String name) {
		this.name = name;
		this.type = type;
	}
}

class Field {
	String name;
	Box type;

	Field(Box type, String name) {
		this.name = name;
		this.type = type;
	}
}

class Box {
	String name;
	Fun[] funs;
	ArrayList<Field> fields = new ArrayList<Field>();
	ArrayList<String> bytes = new ArrayList<String>();

	Box(String name) {
		this.name = name;
	}
}

class NameSpace {
	ArrayList<Edge> edges = new ArrayList<Edge>();
	ArrayList<Cell> cells = new ArrayList<Cell>();

	Cell getCell(String s) {
		for (Cell c : cells)
			if (c.name.equals(s))
				return c;
		return null;
	}

	Edge getEdge(String s) {
		for (Edge e : edges)
			if (e.name.equals(s))
				return e;
		return null;
	}
}

class Memory {
	static byte[] all = new byte[10000];
	static boolean[] free = new boolean[10000];

	static void init() {
		for (int i = 0; i < free.length; i++)
			free[i] = true;
	}

	static int getOne() {
		for (int i = 0; i < all.length; i++)
			if (free[i]) {
				free[i] = false;
				return i;
			}
		System.out.println("memory used up");
		System.exit(-1);
		return 0;
	}
}

public class Machine {
	class Process {
		NameSpace nameSpace = new NameSpace();
		Variable nowVar;
		Fun nowFun; 
		
		public Process(Variable mainVar, Fun mainFun, NameSpace names) {
			nowVar = mainVar;
			nowFun = mainFun;
			nameSpace = names;
			for (Cell c : nowVar.cells)
				nameSpace.cells.add(c);
			for (Edge e : nowVar.edges)
				nameSpace.edges.add(e);
			for (Cell c : constTable)
				nameSpace.cells.add(c);
			nameSpace.edges.add(new Edge("this", nowVar));
		}
		void output(){
			
		}
		void fieldCmd(String[] cmd) {
			Edge whose = nameSpace.getEdge(cmd[2]);
			if (whose.var.getCell(cmd[3]) != null) {
				Cell what = whose.var.getCell(cmd[3]);
				Cell c = nameSpace.getCell(cmd[1]);
				if (c == null) {
					c = new Cell(cmd[1], Memory.getOne());
					nameSpace.cells.add(c);
				}
				Memory.all[c.pointer] = Memory.all[what.pointer];
			} else {
				Edge what = whose.var.getEdge(cmd[3]);
				Edge e = nameSpace.getEdge(cmd[1]);
				if (e == null) {
					e = new Edge(cmd[1], what.var);
					nameSpace.edges.add(e);
				} else
					e.var = what.var;
			}
		}

		void returnCmd(String[] cmd) {
			if (nowFun.type.name.equals("byte")) {
				Cell ret = nameSpace.getCell(cmd[1]);
				retByte = Memory.all[ret.pointer];
			} else if (cmd.length > 1) {
				Edge ret = nameSpace.getEdge(cmd[1]);
				retVar = ret.var;
			}
		}

		void movCmd(String[] cmd) {
			Cell to = nameSpace.getCell(cmd[1]);
			if (to != null) {
				Cell from = nameSpace.getCell(cmd[2]);
				Memory.all[to.pointer] = Memory.all[from.pointer];
			} else {
				Edge src = nameSpace.getEdge(cmd[2]);
				Edge des = nameSpace.getEdge(cmd[1]);
				mov(des.var, src.var);
			}
		}

		void newCmd(String[] cmd) {
			Edge e = nameSpace.getEdge(cmd[1]);
			if (e == null) {
				Variable v = new Variable(boxFromString(cmd[2]), cmd[1]);
				e = new Edge(cmd[1], v);
				nameSpace.edges.add(e);
			}
			Fun fuck = e.var.getFun(e.var.name);
			new Process(e.var, fuck, getArgs(fuck, cmd)).run();
		}

		void declareCmd(String[] cmd) {
			Box box = boxFromString(cmd[0]);
			if (box.name.equals("byte"))
				nameSpace.cells.add(new Cell(cmd[1], Memory.getOne()));
			else
				nameSpace.edges
						.add(new Edge(cmd[1], new Variable(box, cmd[1])));
		}

		void callCmd(String[] cmd) {
			if (nameSpace.getCell(cmd[1]) != null)
				byteFun(cmd);
			else if (nameSpace.getEdge(cmd[1]).var.type.name.equals("console"))
				consoleFun(cmd);
			else {
				Edge he = nameSpace.getEdge(cmd[1]);
				Fun fuck = he.var.getFun(cmd[2]);
				NameSpace she = getArgs(fuck, cmd);
				new Process(he.var, fuck, she).run();
				if (fuck.type.name.equals("byte")) {
					Cell c = nameSpace.getCell(cmd[0]);
					if (c == null) {
						c = new Cell(cmd[0], Memory.getOne());
						nameSpace.cells.add(c);
					}
					Memory.all[c.pointer] = retByte;
				} else {
					Edge e = nameSpace.getEdge(cmd[0]);
					if (e == null) {
						e = new Edge(cmd[0], retVar);
						nameSpace.edges.add(e);
					} else
						e.var = retVar;
				}
			}
		}

		void consoleFun(String[] cmd) {
			System.out.print(Memory.all[nameSpace.getCell(cmd[3]).pointer]);
		}

		private void byteFun(String[] cmd) {
			Cell c = nameSpace.getCell(cmd[0]);
			if (c == null) {
				c = new Cell(cmd[0], Memory.getOne());
				nameSpace.cells.add(c);
			}
			Cell he = nameSpace.getCell(cmd[1]);
			Cell she = nameSpace.getCell(cmd[3]);
			switch (cmd[2]) {
			case "add":
				Memory.all[c.pointer] = (byte) (Memory.all[he.pointer] + Memory.all[she.pointer]);
				break;
			case "sub":
				Memory.all[c.pointer] = (byte) (Memory.all[he.pointer] - Memory.all[she.pointer]);
				break;
			case "mul":
				Memory.all[c.pointer] = (byte) (Memory.all[he.pointer] * Memory.all[she.pointer]);
				break;
			case "div":
				Memory.all[c.pointer] = (byte) (Memory.all[he.pointer] / Memory.all[she.pointer]);
				break;
			case "larger":
				if (Memory.all[he.pointer] > Memory.all[she.pointer])
					Memory.all[c.pointer] = -1;
				else
					Memory.all[c.pointer] = 0;
				break;
			case "less":
				if (Memory.all[he.pointer] < Memory.all[she.pointer])
					Memory.all[c.pointer] = -1;
				else
					Memory.all[c.pointer] = 0;
				break;
			case "equal":
				if (Memory.all[he.pointer] == Memory.all[she.pointer])
					Memory.all[c.pointer] = -1;
				else
					Memory.all[c.pointer] = 0;
				break;
			}
		}
		void output(String s){
			try {
				cout.write(s+"\n");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		void run() { 
			output("**********");
			int i = 0;
			while (true) {
				if (i >= nowFun.body.length) {
					oline("lack return sentence");
					System.exit(-1);
				}
				output(nowFun.body[i]);
				String[] cmd = parseCmd(nowFun.body[i]);
				if (cmd[0].equals("goto"))
					i = Integer.parseInt(cmd[1]);
				else if (cmd[0].equals("if")) {
					Cell c = nameSpace.getCell(cmd[1]);
					if (Memory.all[c.pointer] == 0)
						i = Integer.parseInt(cmd[2]);
					else
						i++;
				} else if (cmd[0].equals("field")) {
					fieldCmd(cmd);
					i++;
				} else if (cmd[0].equals("return")) {
					returnCmd(cmd);
					return;
				} else if (cmd[0].equals("mov")) {// mov ���϶��������µ���ʱ������
					movCmd(cmd);
					i++;
				} else if (cmd[0].equals("new")) {// new �ı�Ȼ���࣬����byte
					newCmd(cmd);
					i++;
				} else if (boxFromString(cmd[0]) != null) {
					declareCmd(cmd);
					i++;
				} else {
					callCmd(cmd);
					i++;
				}
			}
		}

		NameSpace getArgs(Fun f, String[] cmd) {
			NameSpace names = new NameSpace();
			for (int j = 3; j < cmd.length; j++)
				if (f.args[j - 3].type.name.equals("byte")) {
					Cell c = new Cell(f.args[j - 3].name,
							nameSpace.getCell(cmd[j]).pointer);
					names.cells.add(c);
				} else {
					Edge e = new Edge(f.args[j - 3].name,
							nameSpace.getEdge(cmd[j]).var);
					names.edges.add(e);
				}
			return names;
		}

		void mov(Variable to, Variable from) {
			for (int i = 0; i < from.cells.length; i++)
				to.cells[i].pointer = from.cells[i].pointer;
			for (int i = 0; i < from.edges.length; i++)
				to.edges[i].var = to.edges[i].var;
		}

		String[] parseCmd(String s) {
			Scanner cin = new Scanner(s);
			ArrayList<String> cmds = new ArrayList<String>();
			while (cin.hasNext())
				cmds.add(cin.next());
			String[] ans = new String[cmds.size()];
			for (int i = 0; i < ans.length; i++)
				ans[i] = cmds.get(i);
			cin.close();
			return ans;
		}

		boolean isByte(String s) {
			try {
				Byte.parseByte(s);
				return true;
			} catch (Exception e) {
				return false;
			}
		}
	}

	Fun mainFun;
	Variable mainVar;
	Box[] boxTable;
	Cell[] constTable;
	byte retByte;
	Variable retVar;
	FileWriter cout;

	public static void main(String[] args) throws Exception {
		new Machine();
	}

	Machine() throws Exception {
		Memory.init();
		input("asm.txt");
		debugInput();
		init();
		cout=new FileWriter(new File("process.txt"));
		new Process(mainVar, mainFun, new NameSpace()).run();
	}

	void init() {
		for (Box box : boxTable)
			for (Fun f : box.funs)
				if (f.name.equals("main")) {
					mainVar = new Variable(box, "");// ����֮��,����
					mainFun = f;
					return;
				}
		oline("lack main function ");
		System.exit(-1);
	}

	Box boxFromString(String name) {
		for (Box box : boxTable)
			if (box.name.equals(name))
				return box;
		return null;
	}

	void input(String file) throws Exception {
		Scanner cin = new Scanner(new File(file));
		int constNum = Integer.parseInt(cin.next());
		constTable = new Cell[constNum];
		for (int i = 0; i < constNum; i++) {
			int pointer = Memory.getOne();
			Memory.all[pointer] = Byte.parseByte(cin.next());
			constTable[i] = new Cell("#" + i, pointer);
		}
		int boxNum = Integer.parseInt(cin.next());
		boxTable = new Box[boxNum];
		for (int i = 0; i < boxNum; i++)
			boxTable[i] = new Box(cin.next());
		for (Box box : boxTable) {
			int fieldNum = Integer.parseInt(cin.next());
			for (int i = 0; i < fieldNum; i++) {
				Box b = boxFromString(cin.next());
				if (b.name.equals("byte")) {
					box.bytes.add(cin.next());
				} else {
					box.fields.add(new Field(b, cin.next()));
				}
			}
			int funNum = Integer.parseInt(cin.next());
			box.funs = new Fun[funNum];
			for (int i = 0; i < funNum; i++) {
				box.funs[i] = new Fun(boxFromString(cin.next()), cin.next());
				int argNum = Integer.parseInt(cin.next());
				box.funs[i].args = new Field[argNum];
				for (int j = 0; j < argNum; j++)
					box.funs[i].args[j] = new Field(boxFromString(cin.next()),
							cin.next());
				int bodySize = Integer.parseInt(cin.next());
				cin.nextLine();
				box.funs[i].body = new String[bodySize];
				for (int j = 0; j < bodySize; j++)
					box.funs[i].body[j] = cin.nextLine();
			}
		}
		cin.close();
	}

	void debugInput() {
		o("const Table ");
		for (Cell c : constTable)
			o(Memory.all[c.pointer] + " ");
		oline("");
		for (Box box : boxTable) {
			oline(box.name);
			for (Field fi : box.fields) {
				oline("\t" + fi.type + " " + fi.name);
			}
			for (String b : box.bytes) {
				oline("\tbyte " + b);
			}
			for (Fun f : box.funs) {
				o("\t" + f.type.name + " " + f.name + "(");
				for (Field fi : f.args)
					o(fi.type.name + " " + fi.name + ",");
				oline(")");
				for (int i = 0; i < f.body.length; i++)
					oline("\t\t" + i + " * " + f.body[i]);
			}
		}
	}

	void error(String s) {
		System.out.println(s);
	}

	void o(String s) {
		System.out.print(s);
	}

	void oline(String s) {
		o(s + "\n");
	}
}
