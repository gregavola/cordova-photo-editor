# This file is part of the PhotoEditor Software Development Kit.
# Copyright (C) 2016-2018 9elements GmbH <contact@9elements.com>
# All rights reserved.
# Redistribution and use in source and binary forms, without
# modification, are permitted provided that the following license agreement
# is approved and a legal/financial contract was signed by the user.
# The license agreement can be found under the following link:
# https://www.photoeditorsdk.com/LICENSE.txt

# Set working directory to app's frameworks folder
cd "${BUILT_PRODUCTS_DIR}/${FRAMEWORKS_FOLDER_PATH}"
pesdk_folder="./PhotoEditorSDK.framework"

# Signs a binary with the provided identity
codesign() {
  echo "Code Signing ${1} with Identity ${EXPANDED_CODE_SIGN_IDENTITY_NAME}"
  /usr/bin/codesign --force --sign ${EXPANDED_CODE_SIGN_IDENTITY} --preserve-metadata=identifier,entitlements "${1}"
}

# Strips a binary of any architecture not specified in ${VALID_ARCHS}
strip_binary() {
  binary=${1}

  # Get architectures of binary
  archs="$(lipo -info "${binary}" | rev | cut -d ':' -f1 | rev)"
  stripped_archs=""

  for arch in ${archs}; do
    if ! [[ "${VALID_ARCHS}" == *"${arch}"* ]]; then
      # Strip non-valid architecture in-place
      lipo -remove "${arch}" -output "${binary}" "${binary}" || exit 1
      stripped_archs="${stripped_archs} ${arch}"
    fi
  done

  echo "${stripped_archs}"
}

if [[ "${ACTION}" == "install" ]]; then
  # Removing files that are not needed in the final product
  for file in strip-framework.sh PhotoEditorSDK-LICENSE.md; do
    if [[ -e "${pesdk_folder}/${file}" ]]; then
      echo "Removing ${file} from product"
      rm -f "${pesdk_folder}/${file}"
    fi
  done

  # Move BCSymbolMaps to product's folder
  echo "Copying BCSymbolMaps to product's folder"
  find "${pesdk_folder}/BCSymbolMaps" -name '*.bcsymbolmap' -type f -exec mv {} "${CONFIGURATION_BUILD_DIR}" \;
fi

# BCSymbolMaps are not needed at this point because they have either been moved to the archive
# or we're not creating an archive and thus don't need them anyway
rm -rf "${pesdk_folder}/BCSymbolMaps"

# Xcode currently does not support uploading binaries that contain non-device architectures,
# so we are removing them from the framework's binary here
pesdk_binary="${pesdk_folder}/PhotoEditorSDK"

echo "Stripping ${pesdk_binary} of unwanted architectures"
stripped_archs="$(strip_binary "${pesdk_binary}")"

if [[ -n "${stripped_archs}" ]]; then
  echo "Stripped the framework of the following architectures: ${stripped_archs}"

  # We now have to codesign the binary again
  if [[ "${CODE_SIGNING_REQUIRED}" == "YES" ]]; then
    codesign "${pesdk_binary}"
  fi
fi

# Check if input file was specified for script
if [[ -n "${SCRIPT_INPUT_FILE_0}" ]]; then
  dSYM="${SCRIPT_INPUT_FILE_0}"
  dSYM_binary="${dSYM}/Contents/Resources/DWARF/PhotoEditorSDK"

  # Check if file exists and is actually a dSYM
  if [[ "$(file "${dSYM_binary}")" == *"dSYM companion file"* ]]; then
    # Copy dSYM to product's folder
    echo "Copying ${dSYM} to product's directory"
    cp -rf "${dSYM}" "${BUILT_PRODUCTS_DIR}"

    # Strip dSYM binary
    dSYM_binary="${BUILT_PRODUCTS_DIR}/$(basename "${dSYM}")/Contents/Resources/DWARF/PhotoEditorSDK"

    echo "Stripping ${dSYM_binary} of unwanted architectures"
    stripped_archs_dSYM="$(strip_binary "${dSYM_binary}")"

    if [[ -n "${stripped_archs_dSYM}" ]]; then
      echo "Stripped the dSYM of the following architectures: ${stripped_archs_dSYM}"
    fi
  fi
fi
