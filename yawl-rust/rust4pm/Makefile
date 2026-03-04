# Rust4PM Makefile

TARGET := $(shell rustc -vV | grep host | awk '{print $$2}')
ARCH := $(shell uname -m)

CARGO := cargo
BEAM := erl
ERLC := erlc

# Directories
SRC_DIR := src
BUILD_DIR := target/release/deps
ERL_LIBS := /usr/local/lib/erlang/lib

# Build targets
.PHONY: all build clean test install

all: build

build:
	@echo "Building rust4pm NIF..."
	$(CARGO) build --release

clean:
	@echo "Cleaning build artifacts..."
	$(CARGO) clean
	rm -f *.so *.beam

# Install to Erlang lib directory
install: build
	@echo "Installing rust4pm to Erlang lib directory..."
	@mkdir -p $(ERL_LIBS)/rust4pm/ebin
	@cp target/release/librust4pm.so $(ERL_LIBS)/rust4pm/ebin/
	@cp ../priv/erlang/rust4pm.beam $(ERL_LIBS)/rust4pm/ebin/ 2>/dev/null || true
	@echo "Installation complete"

# Test the NIF
test: build
	@echo "Testing rust4pm NIF..."
	$(BEAM) -noshell -eval "
	io:format('Testing NIF loading...~n'),
	case code:load_nif('./target/release/librust4pm.so', []) of
		{ok, _} -> io:format('NIF loaded successfully~n');
		{error, {load_failed, Reason}} -> io:format('NIF load failed: ~p~n', [Reason]);
		{error, Reason} -> io:format('NIF error: ~p~n', [Reason])
	end,
	halt(0)"

# Create Erlang wrapper module
erlang-wrapper:
	@echo "Creating Erlang wrapper module..."
	@mkdir -p ../priv/erlang
	@cat > ../priv/erlang/rust4pm.erl << 'EOF'
-module(rust4pm).
-export([parse_ocel2_json/1, slim_link/2, discover_oc_declare/3, token_replay/2]).

% Convert Erlang term to C-friendly format
to_binary(Term) ->
    term_to_binary(Term).

parse_ocel2_json(JsonTerm) ->
    JsonBinary = to_binary(JsonTerm),
    case rust4pm:parse_ocel2_json(JsonBinary) of
        {error, _} = Error -> Error;
        {ok, ResultBinary} ->
            binary_to_term(ResultBinary)
    end.

slim_link(Source, Target) ->
    SourceBinary = to_binary(Source),
    TargetBinary = to_binary(Target),
    case rust4pm:slim_link(SourceBinary, TargetBinary) of
        {error, _} = Error -> Error;
        {ok, ResultBinary} ->
            binary_to_term(ResultBinary)
    end.

discover_oc_declare(CaseId, EventType, Parameters) ->
    CaseIdBinary = to_binary(CaseId),
    EventTypeBinary = to_binary(EventType),
    ParametersBinary = to_binary(Parameters),
    case rust4pm:discover_oc_declare(CaseIdBinary, EventTypeBinary, ParametersBinary) of
        {error, _} = Error -> Error;
        {ok, ResultBinary} ->
            binary_to_term(ResultBinary)
    end.

token_replay(Net, CaseId) ->
    NetBinary = to_binary(Net),
    CaseIdBinary = to_binary(CaseId),
    case rust4pm:token_replay(NetBinary, CaseIdBinary) of
        {error, _} = Error -> Error;
        {ok, ResultBinary} ->
            binary_to_term(ResultBinary)
    end.
EOF
	@echo "Erlang wrapper created at ../priv/erlang/rust4pm.erl"

# Compile Erlang wrapper
compile-erlang: erlang-wrapper
	@echo "Compiling Erlang wrapper..."
	@mkdir -p ../priv/erlang
	$(ERLC) ../priv/erlang/rust4pm.erl
	@echo "Erlang wrapper compiled"