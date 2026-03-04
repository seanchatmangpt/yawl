# Makefile for QLever SPARQL Client

EBIN_DIR = ../ebin
SRC_DIR = .
INCLUDE_DIR = ../../include
ERLC_FLAGS = +debug_info +warn_unused_vars +warn_shadow_vars +warn_obsolete_guard

MODULES = qlever_client

.SUFFIXES: .erl .beam

$(EBIN_DIR)/%.beam: $(SRC_DIR)/%.erl
	@mkdir -p $(EBIN_DIR)
	erl $(ERLC_FLAGS) -o $(EBIN_DIR) -I $(INCLUDE_DIR) $<

.PHONY: all clean test mock

all: $(foreach module,$(MODULES),$(EBIN_DIR)/$(module).beam)

clean:
	rm -f $(EBIN_DIR)/*.beam
	rm -f *.beam

test:
	@echo "Testing QLever client availability..."
	erl -pa $(EBIN_DIR) -eval "
	io:format('Available: ~p~n', [qlever_client:is_available()]).
	io:format('Ping: ~p~n', [qlever_client:ping()]).
	halt()."

mock:
	@echo "Starting QLever client in mock mode..."
	erl -pa $(EBIN_DIR) -eval "
	qlever_client:start_link([{use_mock, true}, {endpoint, mock}]).
	io:format('Mock mode started~n').
	halt()."