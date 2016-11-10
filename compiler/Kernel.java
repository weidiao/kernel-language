import java.util.*;
import java.io.*;

public class Kernel {
	class FunParser {
		Box box;// 待分析的函数的类名
		Fun fun;// 待分析的函数的函数名
		ArrayList<Label> labels = new ArrayList<Label>();// 标号表
		ArrayList<Goto> gotos = new ArrayList<Goto>();// goto表
		ArrayList<Field> var = new ArrayList<Field>();// 变量表
		int tempNum = 0;// 临时变量值

		public FunParser(Box box, Fun fun) {
			this.box = box;
			this.fun = fun;
			var.addAll(fun.args);
			var.addAll(box.field);
			var.addAll(constTable);
			fun.body = new ArrayList<String>();
			int i = declare();
			parse(i - 1, fun.to);
			must(lines[fun.to], gotos.size() == 0, "goto label didn't defined");
			checkReturn();
		}

		void checkReturn() {
			/*
			 * if cannot reach some sentence ,throw exception; check whether
			 * every branch can return a value;
			 */
		}

		Field fieldFromString(String s) {
			for (Field v : var)
				if (s.equals(v.name))
					return v;
			return null;
		}

		int declare() {
			for (int i = fun.from + 1; i < fun.to;) {
				if (!isBox(words[i]))
					return i;
				must(lines[i + 1], isIdentifier(words[i + 1]),
						"variable '" + words[i + 1] + "' define should has identifier");
				must(lines[i + 1], !isBox(words[i + 1]), "variable '" + words[i + 1] + "' cannot be box name");
				must(lines[i + 1], !isDefined(words[i + 1]), "variable '" + words[i + 1] + "' has defined");
				must(lines[i + 2], words[i + 2].equals(";"), "variable define should has ';'");
				Box box = boxFromString(words[i]);
				fun.body.add(words[i] + " " + words[i + 1]);
				must(lines[i], !box.name.equals("void"), "cannot define void data ");
				var.add(new Field(boxFromString(words[i]), words[i + 1]));
				i += 3;
			}
			return fun.to;
		}

		boolean isDefined(String s) {
			for (Field fi : var)
				if (fi.name.equals(s))
					return true;
			return false;
		}

		int labelSentence(int i) {
			must(lines[i], isIdentifier(words[i]), "variable '" + words[i] + "' define should has identifier");
			must(lines[i], !isBox(words[i]), "variable '" + words[i] + "' cannot be box name");
			must(lines[i], !isDefined(words[i]), "variable '" + words[i] + "' has defined");
			for (int j = 0; j < labels.size(); j++)
				if (labels.get(j).equals(words[i]))
					error(lines[i], "label " + words[i] + " has defined");
			labels.add(new Label(words[i], fun.body.size()));
			for (int j = 0; j < gotos.size(); j++)
				if (gotos.get(j).label.equals(words[i])) {
					fun.body.set(gotos.get(j).address, "goto " + fun.body.size());
					gotos.remove(j);
				}
			return i + 2;
		}

		int gotoSentence(int i) {
			must(lines[i + 2], words[i + 2].equals(";"), "lack ';' in " + box.name + "." + fun.name);
			int j;
			for (j = 0; j < labels.size(); j++)
				if (labels.get(j).name.equals(words[i + 1]))
					break;
			if (j == labels.size()) {
				gotos.add(new Goto(words[i + 1], fun.body.size()));
				fun.body.add("");
			} else
				fun.body.add("goto " + labels.get(j).address);
			return i + 3;
		}

		ArrayList<Field> parseArg(int begin, int end) {// begin=(;end=)
			ArrayList<Field> args = new ArrayList<Field>();
			if (begin + 1 == end)
				return args;// 下面分析至少有一个参数
			int last = begin;
			for (int i = begin + 1; i < end;) {
				if (words[i + 1].equals(",") || i + 1 == end) {
					must(lines[i], i != last, "arg lacked");
					args.add(callSentence(last + 1, i + 1));
					last = i + 1;
					i += 2;
				} else if (words[i + 1].equals("(")) {
					i = match[i + 1] + 1;
				} else
					i++;
			}
			if (last < end)
				args.add(callSentence(last + 1, end));
			return args;
		}

		void checkArg(Fun fun, ArrayList<Field> args) {
			must(lines[fun.from], fun.args.size() == args.size(), "args does't match in " + fun.name);
			for (int i = 0; i < args.size(); i++)
				must(lines[fun.from], args.get(i).type.name.equals(fun.args.get(i).type.name),
						"in " + fun.name + " args don't match");
		}

		Field callSentence(int begin, int end) {// 前闭后开区间end=';'
			int i = begin;
			Field now = null;// 因为一开始now为空，而在后面now不为空，所以没法整。
			if (words[i].equals("new")) {
				must(lines[i + 1], isBox(words[i + 1]), words[i + 1] + " isn't a class name");
				must(lines[i + 2], words[i + 2].equals("("), "lack '('");
				ArrayList<Field> args = parseArg(i + 2, match[i + 2]);
				Fun f = boxFromString(words[i + 1]).getFun(words[i + 1]);
				checkArg(f, args);
				now = new Field(boxFromString(words[i + 1]), "@" + (tempNum++));
				String s = "new " + now.name + " " + f.name + " ";
				for (Field fi : args)
					s += fi.name + " ";
				fun.body.add(s);
				i = match[i + 2] + 1;
			} else if (words[i].equals("(")) {
				now = callSentence(i + 1, match[i]);
				i = match[i] + 1;
			} else if (words[i + 1].equals(".") || i + 1 == end || words[i + 1].equals("=")) {
				now = fieldFromString(words[i]);
				if (now == null) {
					if (isByte(words[i]))
						now = new Field(boxFromString("byte"), words[i]);
					else
						error(lines[i], "the identifier '" + words[i] + "' cannot be parsed");
				}
				if (words[i + 1].equals("=")) {
					if (i + 2 == end)
						error(lines[i + 2], "expression asked after '='");
					Field fi = callSentence(i + 2, end);
					must(lines[i + 2], fi.type.name.equals(now.type.name), "'=' type doesn't match");
					fun.body.add("mov " + now.name + " " + fi.name);
					return fi;
				} else if (i + 2 == end)
					return now;
				else
					i++;
			} else if (words[i + 1].equals("(")) {
				Fun f = box.getFun(words[i]);
				must(lines[i], f != null, " function '" + words[i] + "' hasn't been defined  ");
				ArrayList<Field> args = parseArg(i + 1, match[i + 1]);
				checkArg(f, args);
				now = new Field(f.type, "@" + tempNum++ + " ");
				String s = now.name + " this " + f.name + " ";
				for (Field fi : args)
					s += fi.name + " ";
				fun.body.add(s);
				i = match[i + 1] + 1;
			} else
				error(lines[i], "after " + words[i] + " should be " + "'.' or '(' ");
			while (i < end) {
				must(lines[i], words[i].equals("."), "'.' is asked after " + now.name);
				if (words[i + 2].equals(".") || i + 2 == end || words[i + 2].equals("=")) {
					Field next = now.type.getField(words[i + 1]);
					must(lines[i + 1], next != null, now.type.name + " don't have the field " + words[i + 1]);
					Field temp = new Field(next.type, "@" + tempNum++ + "");
					fun.body.add("field " + temp.name + " " + now.name + " " + next.name);
					now = temp;
					if (words[i + 2].equals("=")) {
						must(lines[i + 3], i + 3 != end, "expression asked after '='");
						Field fi = callSentence(i + 3, end);
						must(lines[i + 3], fi.type.name.equals(now.type.name), "'=' type doesn't match");
						fun.body.add("mov " + now.name + " " + fi.name);
						return fi;
					} else if (i + 2 == end)
						return temp;
					else
						i += 2;
				} else if (words[i + 2].equals("(")) {
					Fun f = now.type.getFun(words[i + 1]);
					must(lines[i + 1], f != null, " function '" + words[i + 1] + "' hasn't been defined");
					ArrayList<Field> args = parseArg(i + 2, match[i + 2]);
					checkArg(f, args);
					Field next = new Field(f.type, "@" + tempNum++ + "");
					String s = next.name + " " + now.name + " " + f.name;
					for (Field fi : args)
						s += " " + fi.name;
					fun.body.add(s);
					now = next;
					i = match[i + 2] + 1;
				} else
					error(lines[i], "after " + words[i] + " should be " + "'.' or '('");
			}
			return now;
		}

		int ifSentence(int i) {
			must(lines[i + 1], words[i + 1].equals("("), "if lacks '('");
			Field ifByte = callSentence(i + 2, match[i + 1]);
			must(lines[i + 2], ifByte.type.name.equals("byte"), "'if(byteTYPE) is asked");
			int ifAddress = fun.body.size();
			fun.body.add("");
			i = match[i + 1] + 1;// i='{'
			must(lines[i], words[i].equals("{"), "if lacks '{'");
			parse(i, match[i]);
			// if end,the follow parse else
			i = match[i] + 1;
			if (words[i].equals("else")) {
				int yesEnd = fun.body.size();
				fun.body.add("");
				fun.body.set(ifAddress, "if " + ifByte.name + " " + fun.body.size());
				must(lines[i + 1], words[i + 1].equals("{"), "else lacks '{'");
				parse(i + 1, match[i + 1]);
				fun.body.set(yesEnd, "goto " + fun.body.size());
				return match[i + 1] + 1;
			} else {
				fun.body.set(ifAddress, "if " + ifByte.name + " " + fun.body.size());
				return i;
			}
		}

		int whileSentence(int i) {
			must(lines[i + 1], words[i + 1].equals("("), "while lacks '('");
			int condition = fun.body.size();
			Field ifByte = callSentence(i + 2, match[i + 1]);
			must(lines[i + 2], ifByte.type.name.equals("byte"), "'while(byteTYPE) is asked");
			int ifAddress = fun.body.size();
			fun.body.add("");
			i = match[i + 1] + 1;
			must(lines[i], words[i].equals("{"), "while lacks '{'");
			parse(i, match[i]);
			fun.body.add("goto " + condition);
			fun.body.set(ifAddress, "if " + ifByte.name + " " + fun.body.size());
			return match[i] + 1;
		}

		int nextSemicolon(int from) {
			int j;
			for (j = from + 1; j < fun.to; j++)
				if (words[j].equals(";"))
					break;
			must(lines[from], j != fun.to, "the sentence lack ';'");
			return j;
		}

		void parse(int begin, int end) {// begin={;end=}
			for (int i = begin + 1; i < end;) {
				if (words[i + 1].equals(":"))
					i = labelSentence(i);
				else if (words[i].equals("goto"))
					i = gotoSentence(i);
				else if (words[i].equals("if"))
					i = ifSentence(i);
				else if (words[i].equals("while"))
					i = whileSentence(i);
				else if (isIdentifier(words[i]) || isByte(words[i])) {
					int j = nextSemicolon(i);
					callSentence(i, j);
					i = j + 1;
				} else if (words[i].equals("return")) {
					if (fun.type.name.equals("void")) {
						must(lines[i + 1], words[i + 1].equals(";"), "void function should 'return ;'");
						fun.body.add("return");
						i += 2;
					} else {
						int j = nextSemicolon(i);
						Field fi = callSentence(i + 1, j);
						must(lines[i], fi.type.name.equals(fun.type.name), "return type doesn't match");
						fun.body.add("return " + fi.name);
						i = j + 1;
					}
				} else
					error(lines[i], "statement error");
			}
		}
	}

	public static void main(String[] args) throws Exception {
		new Kernel();
	}

	String[] words;
	int[] lines;
	int[] match;
	Box[] boxTable;
	ArrayList<Field> constTable;
	ArrayList<Byte> constValue;

	Kernel() throws Exception {
		ArrayList<Character> source = input("source.txt");
		source.addAll(input("lib.txt"));
		split(source);
		debugSplit();
		initMatch();
		debugMatch();
		parseGlobal();
		debugGlobal();
		parseClass();
		checkClass();
		debugClass();
		initConstTable();
		parseFun();
		debugFun();
		export();
	}

	void initConstTable() {
		constTable = new ArrayList<Field>();
		constValue = new ArrayList<Byte>();
		for (int i = 0; i < words.length; i++) {
			if (isByte(words[i])) {
				byte b = Byte.parseByte(words[i]);
				int j;
				for (j = 0; j < constValue.size(); j++)
					if (constValue.get(j) == b)
						break;
				if (j == constValue.size()) {
					words[i] = "#" + constValue.size();
					constValue.add(b);
					constTable.add(new Field(boxFromString("byte"), words[i]));
				} else
					words[i] = "#" + j;
			}
		}
	}

	void export() throws Exception {
		PrintWriter cout = new PrintWriter("asm.txt");
		cout.write(constValue.size() + " ");
		for (Byte b : constValue)
			cout.write(b + " ");
		cout.write(boxTable.length + " ");
		for (Box box : boxTable)
			cout.write(box.name + " ");
		for (Box box : boxTable) {
			cout.write(box.field.size() + " ");
			for (Field fi : box.field)
				cout.write(fi.type.name + " " + fi.name + " ");
			cout.write(box.funs.size() + " ");
			for (Fun f : box.funs) {
				cout.write(f.type.name + " " + f.name + " " + f.args.size() + " ");
				for (Field fi : f.args)
					cout.write(fi.type.name + " " + fi.name + " ");
				cout.write(f.body.size() + "\n");
				for (String s : f.body) {
					cout.write(s + "\n");
				}
			}
		}
		cout.close();
	}

	void initMatch() {
		match = new int[words.length];
		int[] stack = new int[words.length];
		int size = 0;
		for (int i = 0; i < match.length; i++) {
			if (words[i].equals("{") || words[i].equals("(") || words[i].equals("[")) {
				stack[size++] = i;
			} else if (words[i].equals("}")) {
				if (words[stack[size - 1]].equals("{")) {
					match[stack[size - 1]] = i;
					match[i] = stack[size - 1];
					size--;
				} else
					error(lines[i], "embrace don't match");
			} else if (words[i].equals("]")) {
				if (words[stack[size - 1]].equals("[")) {
					match[stack[size - 1]] = i;
					match[i] = stack[size - 1];
					size--;
				} else
					error(lines[i], "embrace don't match");
			} else if (words[i].equals(")")) {
				if (words[stack[size - 1]].equals("(")) {
					match[stack[size - 1]] = i;
					match[i] = stack[size - 1];
					size--;
				} else
					error(lines[i], "embrace don't match");
			} else
				match[i] = -1;
		}
	}

	void debugMatch() {
		oline("debugMatch***********");
		for (int i = 0; i < match.length; i++)
			if (match[i] != -1)
				o(i + "-" + match[i] + " ");
	}

	void parseFun() {
		for (Box box : boxTable)
			for (Fun fun : box.funs)
				new FunParser(box, fun);
	}

	void debugFun() {
		for (Box b : boxTable)
			for (Fun f : b.funs) {
				oline(b.name + " " + f.name + " " + f.from + " " + f.to);
				for (int i = 0; i < f.body.size(); i++) {
					oline("\t\t" + i + " * " + f.body.get(i));
				}
				oline("#############");
			}
	}

	void checkClass() {
		for (int k = 0; k < boxTable.length; k++) {
			Box box = boxTable[k];
			for (int i = k + 1; i < boxTable.length; i++)
				if (boxTable[i].name.equals(box.name))
					error(lines[box.from], "redefined class" + box.name);
			for (int i = 0; i < box.field.size(); i++)
				for (int j = i + 1; j < box.field.size(); j++)
					if (box.field.get(i).name.equals(box.field.get(j).name))
						error(lines[box.from], box.name + " has two " + box.field.get(i).name);
			for (int i = 0; i < box.funs.size(); i++)
				for (int j = i + 1; j < box.funs.size(); j++)
					if (box.funs.get(i).name.equals(box.funs.get(j).name))
						error(lines[box.from], box.name + " has two " + box.funs.get(i).name);
		}
	}

	int parseConstructor(ArrayList<Fun> fun, int i) {
		ArrayList<Field> args = new ArrayList<Field>();
		int j = i + 2;
		while (j < match[i + 1]) {
			must(lines[j], isBox(words[j]), "The function parametre 's type wrong");
			must(lines[j + 1], isIdentifier(words[j + 1]) && !isBox(words[j + 1]) && !words[j + 1].equals("void"),
					"The function parametre isn't an identifier");
			args.add(new Field(boxFromString(words[j]), words[j + 1]));
			j += 2;
			if (words[j].equals(",")) {
				must(lines[j], j + 1 < match[i + 2], "Arguments wrong");
				j++;
			}
		}
		must(lines[j + 1], words[j + 1].equals("{"), "function lack '{' ");
		fun.add(new Fun(j + 1, match[j + 1], boxFromString("void"), words[i], args));
		return match[j + 1] + 1;
	}

	int parseFun(ArrayList<Fun> fun, int i) {
		ArrayList<Field> args = new ArrayList<Field>();
		int j = i + 3;
		while (j < match[i + 2]) {
			must(lines[j], isBox(words[j]), "The function parametre 's type wrong");
			must(lines[j + 1], isIdentifier(words[j + 1]) && !isBox(words[j + 1]) && !words[j + 1].equals("void"),
					"The function parametre isn't an identifier");
			args.add(new Field(boxFromString(words[j]), words[j + 1]));
			j += 2;
			if (words[j].equals(",")) {
				must(lines[j], j + 1 < match[i + 2], "Arguments wrong");
				j++;
			}
		}
		must(lines[j + 1], words[j + 1].equals("{"), "function lack '{' ");
		fun.add(new Fun(j + 1, match[j + 1], boxFromString(words[i]), words[i + 1], args));
		return match[j + 1] + 1;
	}

	void parseClass() {
		for (Box box : boxTable) {
			ArrayList<Field> fi = new ArrayList<Field>();
			ArrayList<Fun> fun = new ArrayList<Fun>();
			int i = box.from + 3;
			while (i < box.to) {
				must(lines[i], isBox(words[i]), "The member in the class must has a type");
				if (words[i + 1].equals("(")) {
					i = parseConstructor(fun, i);
				} else if (isIdentifier(words[i + 1])) {
					if (words[i + 2].equals(";")) {
						must(lines[i], !words[i].equals("void"), "only functions can be void type");
						fi.add(new Field(boxFromString(words[i]), words[i + 1]));
						i += 3;
					} else {
						must(lines[i + 2], words[i + 2].equals("("), words[i + 2] + " should be '(' or ';'");
						i = parseFun(fun, i);
					}
				} else
					error(lines[i], "cannot parse this line");
			}
			box.field = fi;
			box.funs = fun;
		}
	}

	void debugClass() {
		for (Box b : boxTable) {
			oline(b.name);
			oline("   field **********");
			for (Field fi : b.field) {
				oline("\t" + fi.type.name + " " + fi.name);
			}
			oline("   funs **********");
			for (Fun fun : b.funs) {
				o("\t" + fun.type.name + " " + fun.name + " (");
				for (Field arg : fun.args) {
					o(arg.type.name + " " + arg.name + ",");
				}
				o(")");
				oline("--" + fun.from + " " + fun.to);
			}
		}
	}

	void parseGlobal() {
		ArrayList<Box> table = new ArrayList<Box>();
		int i = 0;
		while (i < words.length) {
			must(lines[i], words[i].equals("class"), "'class' lacked ");
			must(lines[i], isIdentifier(words[i + 1]), "class name isn't an idertifier ");
			must(lines[i], words[i + 2].equals("{"), "class lack '{'");
			table.add(new Box(i, match[i + 2], words[i + 1]));
			i = match[i + 2] + 1;
		}
		boxTable = new Box[table.size()];
		for (i = 0; i < boxTable.length; i++)
			boxTable[i] = table.get(i);
	}

	void debugGlobal() {
		oline("The source file contains " + boxTable.length + " class :");
		for (Box b : boxTable)
			oline("\t" + b.name + " " + b.from + " " + b.to);
	}

	Box boxFromString(String s) {
		for (Box b : boxTable)
			if (b.name.equals(s))
				return b;
		return null;
	}

	boolean isKeyWord(String s) {
		String keys = "if while class return goto new";
		String[] keyWords = keys.split(" ");
		for (String word : keyWords)
			if (s.equals(word))
				return true;
		return false;
	}

	boolean isBox(String s) {
		for (Box b : boxTable)
			if (b.name.equals(s))
				return true;
		return false;
	}

	boolean isByte(String s) {
		try {
			Byte.parseByte(s);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	boolean isIdentifier(String s) {
		if (!isAlpha(s.charAt(0)))
			return false;
		if (isKeyWord(s))
			return false;
		for (int i = 1; i < s.length(); i++)
			if (!isAlpha(s.charAt(i)) && !isDigit(s.charAt(i)))
				return false;
		return true;
	}

	boolean isSeparator(char c) {
		final char[] separator = "[]{}(),.:;=".toCharArray();
		for (int i = 0; i < separator.length; i++)
			if (c == separator[i])
				return true;
		return false;
	}

	boolean isAlpha(char c) {
		if (c <= 'z' && c >= 'a')
			return true;
		if (c <= 'Z' && c >= 'A')
			return true;
		return false;
	}

	boolean isDigit(char c) {
		if (c >= '0' && c <= '9')
			return true;
		else
			return false;
	}

	void split(ArrayList<Character> source) {
		int i = 0;
		while (i < source.size()) {
			if (isSeparator(source.get(i))) {
				source.add(i, ' ');
				i += 2;
				source.add(i, ' ');
			} else
				i++;
		}
		String all = "";
		for (Character c : source)
			all += c;
		Scanner cin = new Scanner(all);
		ArrayList<String> words = new ArrayList<String>();
		ArrayList<Integer> lines = new ArrayList<Integer>();
		int line = 0;
		while (cin.hasNext()) {
			Scanner ss = new Scanner(cin.nextLine());
			line++;
			while (ss.hasNext()) {
				words.add(ss.next());
				lines.add(line);
			}
			ss.close();
		}
		cin.close();
		this.words = new String[words.size()];
		this.lines = new int[words.size()];
		for (i = 0; i < words.size(); i++) {
			this.words[i] = words.get(i);
			this.lines[i] = lines.get(i);
		}
	}

	void debugSplit() {
		for (int i = 0; i < words.length; i++)
			oline(i + " * " + words[i] + " - " + lines[i]);
	}

	ArrayList<Character> input(String fileName) throws Exception {
		FileReader cin = new FileReader(fileName);
		ArrayList<Character> source = new ArrayList<Character>();
		for (int i = cin.read(); i != -1; i = cin.read())
			source.add((char) i);
		cin.close();
		return source;
	}

	void must(int line, boolean b, String ifFalse) {
		if (!b)
			error(line, ifFalse);
	}

	void error(int line, String s) {
		System.err.println(line + " : " + s);
		System.exit(0);
	}

	void o(String s) {
		System.out.print(s);
	}

	void oline(String s) {
		System.out.println(s);
	}

	void oline() {
		oline("");
	}
}
