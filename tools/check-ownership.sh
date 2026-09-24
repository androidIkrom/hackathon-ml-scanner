#!/usr/bin/env bash
# Usage: tools/check-ownership.sh <A|I|Y|C> <file>...
# Fails when a file belongs to somebody else according to OWNERS (longest prefix wins).
set -euo pipefail

who="$1"
shift
root="$(git rev-parse --show-toplevel)"
owners="$root/OWNERS"
status=0

for file in "$@"; do
    # Tests belong to the owner of the package they test.
    path="${file/\/src\/test\/java\//\/src\/main\/java\/}"
    path="${path/\/src\/androidTest\/java\//\/src\/main\/java\/}"
    best=""
    best_owner=""
    while read -r prefix owner _; do
        [[ -z "$prefix" || "$prefix" == \#* ]] && continue
        if [[ "$path" == "$prefix"* && ${#prefix} -gt ${#best} ]]; then
            best="$prefix"
            best_owner="$owner"
        fi
    done < "$owners"
    if [[ -z "$best_owner" ]]; then
        echo "UNOWNED   $file  (ask A to add its folder to OWNERS)"
        status=1
    elif [[ "$best_owner" != "$who" ]]; then
        echo "NOT YOURS $file  (owner: $best_owner, you are: $who)"
        status=1
    fi
done
exit $status
