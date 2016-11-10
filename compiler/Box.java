
import java.util.ArrayList;

class Field {
	Box type;
	String name;

	Field(Box type, String name) {
		this.name = name;
		this.type = type;
	}
}

public class Box {
	String name;
	ArrayList<Field> field;
	ArrayList<Fun> funs;
	int from;
	int to;

	public Box(int from, int to, String name) {
		this.from = from;
		this.to = to;
		this.name = name;
	}

	Field getField(String s) {
		for (Field fi : field)
			if (fi.name.equals(s))
				return fi;
		return null;
	}

	Fun getFun(String s) {
		for (Fun f : funs)
			if (f.name.equals(s))
				return f;
		return null;
	}
}

class Fun {
	String name;
	Box type;
	ArrayList<Field> args;
	ArrayList<String> body;
	int from;
	int to;

	Fun(int from, int to, Box type, String name, ArrayList<Field> args) {
		this.from = from;
		this.to = to;
		this.type = type;
		this.name = name;
		this.args = args;
	}
}

class Label {
	String name;
	int address;

	Label(String s, int addr) {
		name = s;
		address = addr;
	}
}

class Goto {
	int address;
	String label;

	Goto(String label, int addr) {
		this.address = addr;
		this.label = label;
	}
}
